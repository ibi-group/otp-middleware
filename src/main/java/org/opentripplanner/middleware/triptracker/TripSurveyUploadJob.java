package org.opentripplanner.middleware.triptracker;

import org.apache.logging.log4j.util.Strings;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadFiles;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadJob;
import org.opentripplanner.middleware.connecteddataplatform.ReportingInterval;
import org.opentripplanner.middleware.models.TripSurveyUpload;
import org.opentripplanner.middleware.typeform.Responses;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.typeform.TypeFormDispatcher;
import org.opentripplanner.middleware.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TripSurveyUploadJob extends IntervalUploadJob<TripSurveyUpload> {
    private static final Logger LOG = LoggerFactory.getLogger(TripSurveyUploadJob.class);

    public static final String SURVEY_ZIP_FILE_PREFIX = "merged";

    private final Function<LocalDateTime, Responses> surveyApiResponseProvider;

    private final String csvHeaders;

    public TripSurveyUploadJob() {
        this(TypeFormDispatcher::downloadSurveyResponses, TypeFormDispatcher::downloadSurveyHeaders);
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
        // TODO: Add support if more than 1000 responses are submitted the same day.
        if (apiResponse != null && processSurveyHistory(upload, apiResponse)) {
            // If successfully compiled and updated, update the status to 'completed'.
            markAsCompleted(upload);

            // Attempt to delete the responses that were downloaded above
            List<String> ids = apiResponse.items.stream().map(i -> i.response_id).collect(Collectors.toList());
            TypeFormDispatcher.deleteSurveyResponses(ids);
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

    public static boolean isConfigured() {
        return !Strings.isBlank(CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME) && TypeFormDispatcher.checkSurveyIdAndToken();
    }
}
