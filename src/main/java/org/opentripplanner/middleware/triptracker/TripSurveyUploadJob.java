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
import org.opentripplanner.middleware.models.typeform.Responses;
import org.opentripplanner.middleware.models.typeform.Form;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.ZIP_FILE_EXTENSION;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveyUploadJob extends IntervalUploadJob<TripSurveyUpload> {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveyUploadJob.class);

    public static final String TRIP_SURVEY_API_TOKEN = getConfigPropertyAsText("TRIP_SURVEY_API_TOKEN");

    public static final String TRIP_SURVEY_ID = getConfigPropertyAsText("TRIP_SURVEY_ID");

    public static final String SURVEY_ZIP_FILE_PREFIX = "merged";

    public static final String SURVEY_ZIP_FILE_NAME = SURVEY_ZIP_FILE_PREFIX + ".zip";

    private final Function<LocalDateTime, Responses> surveyApiResponseProvider;

    private final String csvHeaders;

    public TripSurveyUploadJob() {
        this(TripSurveyUploadJob::downloadSurveyResponses, TripSurveyUploadJob::downloadSurveyHeaders);
    }

    public TripSurveyUploadJob(
        Function<LocalDateTime, Responses> surveyApiResponseProvider,
        Supplier<String> surveyCsvHeaderProvider
    ) {
        super(LOG, ReportingInterval.DAILY, Persistence.tripSurveyUploads);
        this.surveyApiResponseProvider = surveyApiResponseProvider;
        this.csvHeaders = surveyCsvHeaderProvider.get();
    }

    @Override
    protected void processInterval(TripSurveyUpload upload) {
        Responses apiResponse = surveyApiResponseProvider.apply(upload.uploadHour);

        if (apiResponse != null) {
            // Dump responses to temp CSV/Zip file and upload to S3.
            boolean success = processSurveyHistory(upload, apiResponse);

            if (success) {
                // If successfully compiled and updated, update the status to 'completed'.
                upload.status = TripHistoryUploadStatus.COMPLETED.getValue();
                Persistence.tripSurveyUploads.replace(upload.id, upload);

                // Attempt to delete the responses that were downloaded above
                List<String> ids = apiResponse.items.stream().map(i -> i.response_id).collect(Collectors.toList());
                deleteSurveyResponses(ids);
            }
        }
    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripSurveyUploads.create(new TripSurveyUpload(time));
    }

    private static HttpResponseValues apiRequest(HttpMethod method, String subPath, String queryParams, String topic) {
        if (checkSurveyIdAndToken()) {
            HttpResponseValues response = HttpUtils.httpRequestRawResponse(
                URI.create(String.format("https://api.typeform.com/forms/%s%s%s", TRIP_SURVEY_ID, subPath, queryParams)),
                30,
                method,
                Map.of("Authorization", String.format("Bearer %s", TRIP_SURVEY_API_TOKEN)),
                null
            );

            if (response.status != HttpStatus.OK_200) {
                LOG.warn("Error {}-ing {}: [{}] {}", method, topic, response.status, response.responseBody);
            }

            return response;
        }
        return null;
    }

    private static Responses downloadSurveyResponses(LocalDateTime day) {
        HttpResponseValues response = apiRequest(GET, "/responses", responsesParams(day), "survey responses");
        if (response != null && response.status == HttpStatus.OK_200) {
            try {
                return JsonUtils.getPOJOFromJSON(response.responseBody, Responses.class);
            } catch (JsonProcessingException e) {
                LOG.warn("Error parsing survey responses", e);
            }
        }

        return null;
    }

    private static String downloadSurveyHeaders() {
        HttpResponseValues response = apiRequest(GET, "", "", "survey headers");
        if (response != null && response.status == HttpStatus.OK_200) {
            try {
                Form form = JsonUtils.getPOJOFromJSON(response.responseBody, Form.class);
                return form.toCsvHeader();
            } catch (JsonProcessingException e) {
                LOG.warn("Error parsing survey headers", e);
            }
        }

        return null;
    }

    private static void deleteSurveyResponses(List<String> ids) {
        if (!ids.isEmpty()) {
            String idParam = String.format("?included_response_ids=%s", String.join(",", ids));
            apiRequest(DELETE, "/responses", idParam, "survey responses");
        }
    }

    public static boolean checkSurveyIdAndToken() {
        boolean idAndTokenPresent = !Strings.isBlank(TRIP_SURVEY_API_TOKEN) && !Strings.isBlank(TRIP_SURVEY_ID);
        if (!idAndTokenPresent) {
            LOG.warn("Survey ID or survey response API token was not provided.");
        }
        return idAndTokenPresent;
    }

    public static String responsesParams(LocalDateTime day) {
        ZonedDateTime zonedDay = day.atZone(DateTimeUtils.getOtpZoneId());
        return String.format(
            "?page_size=1000&since=%d&until=%d",
            zonedDay.toEpochSecond(),
            zonedDay.plusDays(1).minusSeconds(1).toEpochSecond()
        );
    }

    public boolean processSurveyHistory(TripSurveyUpload upload, Responses response) {
        // Dump CSV content to file
        String filePrefix = getFilePrefix(upload.uploadHour, SURVEY_ZIP_FILE_PREFIX);
        String zipFileName = String.join(".", filePrefix, ZIP_FILE_EXTENSION);
        String tempFileFolder = FileUtils.getTempDirectory().getAbsolutePath();
        String tempZipFile = String.join(File.separator, tempFileFolder, filePrefix + ".zip");
        String tempDataFile = String.join(File.separator, tempFileFolder, filePrefix + ".csv");

        try {
            FileUtils.writeToFile(tempDataFile, false, response.toCsv(csvHeaders));

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
            return false;
        } finally {
            // Delete the temporary files here, to cover S3 upload success or failure.
            try {
                LOG.info("Deleting survey zip file {}.", tempZipFile);
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

        return true;
    }
}
