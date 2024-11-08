package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadJob;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;
import org.opentripplanner.middleware.models.IntervalUpload;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.ZIP_FILE_EXTENSION;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStringFromDate;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveyUploadJob extends IntervalUploadJob {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveyUploadJob.class);

    private static final String TRIP_SURVEY_API_TOKEN = getConfigPropertyAsText("TRIP_SURVEY_API_TOKEN");

    private static final String TRIP_SURVEY_ID = getConfigPropertyAsText("TRIP_SURVEY_ID");

    public static final String SURVEY_ZIP_FILE_PREFIX = "merged";

    public static final String SURVEY_ZIP_FILE_NAME = SURVEY_ZIP_FILE_PREFIX + ".zip";

    public TripSurveyUploadJob() {
        super(LOG, true);
    }

    @Override
    protected void runInnerLogic() {
        List<TripSurveyUpload> incompleteUploads = getIncompleteUploads();
        incompleteUploads.forEach(upload -> {
            boolean success = true;

            // Get access token
            if (!Strings.isBlank(TRIP_SURVEY_API_TOKEN)) {
                TypeFormTripSurveyApiResponse apiResponse = downloadSurveyResponses(upload.uploadHour);

                // Dump responses to temp CSV/Zip file and upload to S3.
                processSurveyHistory(upload, apiResponse, false);

                success = apiResponse != null;
            } else {
                LOG.warn("Survey response token was not provided.");
            }

            // Download survey responses for the indicated day

            if (success) {
                // If successfully compiled and updated, update the status to 'completed' and record the number of trip
                // requests uploaded (if any).
                upload.status = TripHistoryUploadStatus.COMPLETED.getValue();
                Persistence.tripSurveyUploads.replace(upload.id, upload);
            }
        });
    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripSurveyUploads.create(new TripSurveyUpload(time));
    }

    @Override
    protected IntervalUpload getLastUploadCreated() {
        return TripSurveyUpload.getLastCreated();
    }

    /**
     * Get all incomplete trip survey uploads.
     * TODO: Deduplicate
     */
    public static List<TripSurveyUpload> getIncompleteUploads() {
        FindIterable<TripSurveyUpload> incompleteUploads = Persistence.tripSurveyUploads.getFiltered(
            Filters.ne("status", TripHistoryUploadStatus.COMPLETED.getValue())
        );
        return incompleteUploads.into(new ArrayList<>());
    }

    private TypeFormTripSurveyApiResponse downloadSurveyResponses(LocalDateTime day) {
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
                return null;
            }
        }

        LOG.warn("Error getting survey responses - code: {}, message: {}", response.status, response.responseBody);
        return null;
    }

    public static String makeSurveyResponseUrl(String surveyId, LocalDateTime day) {
        return String.format(
            "https://api.typeform.com/forms/%s/responses?page_size=1000&since=%s&until=%s",
            surveyId,
            day,
            day.plusDays(1).minusSeconds(1)
        );
    }

    /**
     * Produce file name without path or extension.
     * TODO: reuse
     */
    public static String getFilePrefix(LocalDateTime date, String entityName) {
        final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";
        return String.format(
            "%s-%s",
            getStringFromDate(date, DEFAULT_DATE_FORMAT_PATTERN),
            entityName
        );
    }

    public void processSurveyHistory(TripSurveyUpload upload, TypeFormTripSurveyApiResponse response, boolean isTest) {
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
                if (!isTest) {
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
