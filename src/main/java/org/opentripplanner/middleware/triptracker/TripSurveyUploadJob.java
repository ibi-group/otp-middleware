package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadJob;
import org.opentripplanner.middleware.connecteddataplatform.ReportingInterval;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;
import org.opentripplanner.middleware.models.TripSurveyUpload;
import org.opentripplanner.middleware.models.TypeFormTripSurveyApiResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.S3Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.Map;

import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.ZIP_FILE_EXTENSION;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveyUploadJob extends IntervalUploadJob<TripSurveyUpload> {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveyUploadJob.class);

    private static final String TRIP_SURVEY_API_TOKEN = getConfigPropertyAsText("TRIP_SURVEY_API_TOKEN");

    private static final String TRIP_SURVEY_ID = getConfigPropertyAsText("TRIP_SURVEY_ID");

    public static final String SURVEY_ZIP_FILE_PREFIX = "merged";

    public static final String SURVEY_ZIP_FILE_NAME = SURVEY_ZIP_FILE_PREFIX + ".zip";

    private final Function<LocalDateTime, TypeFormTripSurveyApiResponse> surveyApiResponseProvider;

    public TripSurveyUploadJob() {
        super(LOG, ReportingInterval.DAILY, Persistence.tripSurveyUploads);
        this.surveyApiResponseProvider = this::downloadSurveyResponses;
    }

    /** Used for tests. */
    public TripSurveyUploadJob(Function<LocalDateTime, TypeFormTripSurveyApiResponse> surveyApiResponseProvider) {
        super(LOG, ReportingInterval.DAILY, Persistence.tripSurveyUploads);
        this.surveyApiResponseProvider = surveyApiResponseProvider;
    }

    @Override
    protected void processInterval(TripSurveyUpload upload) {
        TypeFormTripSurveyApiResponse apiResponse = surveyApiResponseProvider.apply(upload.uploadHour);

        if (apiResponse != null) {
            // Dump responses to temp CSV/Zip file and upload to S3.
            processSurveyHistory(upload, apiResponse);

            // If successfully compiled and updated, update the status to 'completed' and record the number of trip
            // requests uploaded (if any).
            upload.status = TripHistoryUploadStatus.COMPLETED.getValue();
            Persistence.tripSurveyUploads.replace(upload.id, upload);
        }
    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripSurveyUploads.create(new TripSurveyUpload(time));
    }

    private TypeFormTripSurveyApiResponse downloadSurveyResponses(LocalDateTime day) {
        if (!Strings.isBlank(TRIP_SURVEY_API_TOKEN) && !Strings.isBlank(TRIP_SURVEY_ID)) {
            HttpResponseValues response = HttpUtils.httpRequestRawResponse(
                URI.create(makeSurveyResponseUrl(TRIP_SURVEY_ID, day)),
                30,
                HttpMethod.GET,
                Map.of("Authorization", String.format("Bearer %s", TRIP_SURVEY_API_TOKEN)),
                null
            );

            if (response.status == HttpStatus.OK_200) {
                try {
                    return JsonUtils.getPOJOFromJSON(response.responseBody, TypeFormTripSurveyApiResponse.class);
                } catch (JsonProcessingException e) {
                    LOG.warn("Error parsing survey responses: {}", e);
                }
            }

            LOG.warn("Error getting survey responses - code: {}, message: {}", response.status, response.responseBody);
        } else {
            LOG.warn("Survey ID or survey response API token was not provided.");
        }

        return null;
    }

    public static String makeSurveyResponseUrl(String surveyId, LocalDateTime day) {
        return String.format(
            "https://api.typeform.com/forms/%s/responses?page_size=1000&since=%s&until=%s",
            surveyId,
            day.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            day.plusDays(1).minusSeconds(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    public void processSurveyHistory(TripSurveyUpload upload, TypeFormTripSurveyApiResponse response) {
        // Dump CSV content to file
        String filePrefix = getFilePrefix(upload.uploadHour, SURVEY_ZIP_FILE_PREFIX);
        String zipFileName = String.join(".", filePrefix, ZIP_FILE_EXTENSION);
        String tempFileFolder = FileUtils.getTempDirectory().getAbsolutePath();
        String tempZipFile = String.join(File.separator, tempFileFolder, filePrefix + ".zip");
        String tempDataFile = String.join(File.separator, tempFileFolder, filePrefix + ".csv");

        try {
            FileUtils.writeToFile(tempDataFile, false, response.toCsv());

            // Upload the file if records were written or config setting requires uploading blank files.
            FileUtils.addSingleFileToZip(tempDataFile, tempZipFile);
            S3Utils.putObject(
                CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                String.format(
                    "%s/%s",
                    "trip-survey-responses",
                    zipFileName
                ),
                new File(tempZipFile)
            );
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag(
                String.format("Failed to write survey data for (%s)", upload.uploadHour),
                e
            );
        } finally {
            // Delete the temporary files. This is done here in case the S3 upload fails.
            try {
                LOG.error("Deleting CDP zip file {} as an error occurred while processing the data it was supposed to contain.", tempZipFile);
                FileUtils.deleteFile(tempDataFile);
                if (!response.isTest) {
                    FileUtils.deleteFile(tempZipFile);
                } else {
                    LOG.warn("In test mode, temp zip file {} not deleted. This is expected to be deleted by the calling test.",
                        tempZipFile
                    );
                }
            } catch (IOException e) {
                LOG.error("Failed to delete temp files", e);
            }
        }
    }
}
