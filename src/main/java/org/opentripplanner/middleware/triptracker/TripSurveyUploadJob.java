package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadFiles;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadJob;
import org.opentripplanner.middleware.connecteddataplatform.ReportingInterval;
import org.opentripplanner.middleware.models.TripSurveyUpload;
import org.opentripplanner.middleware.typeform.Responses;
import org.opentripplanner.middleware.typeform.Form;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveyUploadJob extends IntervalUploadJob<TripSurveyUpload> {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveyUploadJob.class);

    public static final String TRIP_SURVEY_API_TOKEN = getConfigPropertyAsText("TRIP_SURVEY_API_TOKEN");

    public static final String TRIP_SURVEY_ID = getConfigPropertyAsText("TRIP_SURVEY_ID");

    public static final String SURVEY_ZIP_FILE_PREFIX = "merged";

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

        // Dump responses to temp CSV/Zip file and upload to S3.
        if (apiResponse != null && processSurveyHistory(upload, apiResponse)) {
            // If successfully compiled and updated, update the status to 'completed'.
            markAsCompleted(upload);

            // Attempt to delete the responses that were downloaded above
            List<String> ids = apiResponse.items.stream().map(i -> i.response_id).collect(Collectors.toList());
            deleteSurveyResponses(ids);
        }
    }

    @Override
    protected void createUpload(LocalDateTime time) {
        Persistence.tripSurveyUploads.create(new TripSurveyUpload(time));
    }

    public boolean processSurveyHistory(TripSurveyUpload upload, Responses responses) {
        IntervalUploadFiles uploadFiles = new IntervalUploadFiles(
            ConnectedDataManager.getFilePrefix(reportingInterval, upload.uploadHour, SURVEY_ZIP_FILE_PREFIX),
            "csv",
            responses.isTest
        );

        // Dump CSV content to file
        String tempDataFile = uploadFiles.getTempDataFile();
        try (uploadFiles) {
            FileUtils.writeToFile(tempDataFile, false, responses.toCsv(csvHeaders));
            uploadFiles.compressAndUpload("trip-survey-responses");
            return true;
        } catch (IOException e) {
            LOG.warn("Error writing survey results to {}", tempDataFile);
            return false;
        }
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

    /** Assembles the query params for retrieving TypeForm survey responses. */
    public static String responsesParams(LocalDateTime day) {
        ZonedDateTime zonedDay = day.atZone(DateTimeUtils.getOtpZoneId());
        // The page_size param needs to be passed. Without it, only up to 25 responses are returned by TypeForm.
        // TypeForm can return up to 1000 responses in one query, see
        // https://www.typeform.com/developers/responses/reference/retrieve-responses/.
        return String.format(
            "?page_size=1000&since=%d&until=%d",
            zonedDay.toEpochSecond(),
            zonedDay.plusDays(1).minusSeconds(1).toEpochSecond()
        );
    }
}
