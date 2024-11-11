package org.opentripplanner.middleware.triptracker;

import jersey.repackaged.com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadStatus;
import org.opentripplanner.middleware.models.TripSurveyUpload;
import org.opentripplanner.middleware.models.TypeFormTripSurveyApiResponse;
import org.opentripplanner.middleware.models.TypeFormTripSurveyApiResponseTest;
import org.opentripplanner.middleware.models.TypeFormTripSurveyResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.S3Utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.getDailyFileName;
import static org.opentripplanner.middleware.models.TypeFormTripSurveyResponseTest.makeResponse;
import static org.opentripplanner.middleware.utils.FileUtils.getContentsOfFileInZip;

class TripSurveyUploadJobTest extends OtpMiddlewareTestEnvironment {

    private static TripSurveyUpload surveyUploadTwoDaysAgo;
    private static TripSurveyUpload surveyUploadThreeDaysAgo;
    private static List<TripSurveyUpload> surveyUploads;

    private static final LocalDateTime PREVIOUS_WHOLE_DAY_FROM_NOW = DateTimeUtils.getPreviousDayFrom(LocalDateTime.now());

    private String tempFile;
    private String zipFileName;

    @BeforeAll
    public static void setUp() {
        assumeTrue(IS_END_TO_END);

        // Create records of previous trip survey uploads.
        LocalDateTime twoDaysAgo = LocalDateTime.ofInstant(Instant.now().minus(2, ChronoUnit.DAYS), DateTimeUtils.getOtpZoneId());
        LocalDateTime threeDaysAgo = LocalDateTime.ofInstant(Instant.now().minus(3, ChronoUnit.DAYS), DateTimeUtils.getOtpZoneId());

        surveyUploadTwoDaysAgo = new TripSurveyUpload("upload-2", twoDaysAgo, TripHistoryUploadStatus.PENDING);
        surveyUploadThreeDaysAgo = new TripSurveyUpload("upload-3", threeDaysAgo, TripHistoryUploadStatus.COMPLETED);

        surveyUploads = Lists.newArrayList(surveyUploadTwoDaysAgo, surveyUploadThreeDaysAgo);

        // TODO: Add logic to avoid these previous uploads
        // Persistence.tripSurveyUploads.create(surveyUploadTwoDaysAgo);
        // Persistence.tripSurveyUploads.create(surveyUploadThreeDaysAgo);
    }

    @AfterAll
    public static void tearDown() {
        assumeTrue(IS_END_TO_END);
        
        // Delete trip survey upload entries
        surveyUploads.forEach(upload -> {
            TripSurveyUpload storedUpload = Persistence.tripSurveyUploads.getById(upload.id);
            if (storedUpload != null) {
                Persistence.tripSurveyUploads.removeById(storedUpload.id);
            }
        });
    }
    
    @AfterEach
    void afterEach() throws Exception {
        assumeTrue(IS_END_TO_END);

        // Delete trip survey upload entries that were added.
        for (TripSurveyUpload upload : Persistence.tripSurveyUploads.getAll()) {
            if (!(upload.id.equals(surveyUploadTwoDaysAgo.id) && !upload.id.equals(surveyUploadThreeDaysAgo.id))) {
                Persistence.tripSurveyUploads.removeById(upload.id);
            }
        }

        // Delete any files we created
        if (tempFile != null) {
            FileUtils.deleteFile(tempFile);
            tempFile = null;
        }
        if (zipFileName != null) {
            S3Utils.deleteObject(
                ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                String.format("%s/%s", ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME, zipFileName)
            );
            zipFileName = null;
        }
    }

    /**
     * Make sure that the first upload is created and contains the correct upload date.
     */
    @Test
    void canStageFirstUpload() {
        TripSurveyUploadJob job = new TripSurveyUploadJob();
        job.stageUploadDays();
        TripSurveyUpload upload = TripSurveyUpload.getFirst();
        assertNotNull(upload);
        assertTrue(PREVIOUS_WHOLE_DAY_FROM_NOW.isEqual(upload.uploadHour));
    }

    @Test
    void canRunJob() {
        assumeTrue(IS_END_TO_END);

        // After running the job, assuming that API calls were successful,
        // the upload records should be updated.

        TripSurveyUploadJob job = new TripSurveyUploadJob(localDateTime -> createSurveyApiResponse());
        job.run();

        TripSurveyUpload upload = TripSurveyUpload.getLastCreated();
        assertNotNull(upload);
        assertTrue(PREVIOUS_WHOLE_DAY_FROM_NOW.isEqual(upload.uploadHour));
        assertEquals(TripHistoryUploadStatus.COMPLETED.toString(), upload.status);
    }

    @Test
    void canMakeSurveyResponseURL() {
        LocalDateTime date = LocalDateTime.of(2024, 10, 25, 0, 0);

        String s = TripSurveyUploadJob.makeSurveyResponseUrl("survey-id", date);

        // All times in the time zone of this server instance.
        assertEquals("https://api.typeform.com/forms/survey-id/responses?page_size=1000&since=2024-10-25T00:00&until=2024-10-25T23:59:59", s);
    }

    /**
     * Confirm that a single zip file is created which contains the compiled survey responses (CSV). Also confirm that the contents
     * written to the CSV file is correct and covers a single day's worth of responses.
     * TODO: Try to combine code from ConnectedDataPlatformTest
     */
    @Test
    void canCreateZipFileWithContent() throws Exception {
        assumeTrue(IS_END_TO_END);

        TypeFormTripSurveyApiResponse apiResponse = createSurveyApiResponse();

        TripSurveyUploadJob job = new TripSurveyUploadJob(localDateTime -> apiResponse);
        job.stageUploadDays();
        job.processSurveyHistory(TripSurveyUpload.getLastCreated(), apiResponse);
        zipFileName = getDailyFileName(PREVIOUS_WHOLE_DAY_FROM_NOW, TripSurveyUploadJob.SURVEY_ZIP_FILE_NAME);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getDailyFileName(PREVIOUS_WHOLE_DAY_FROM_NOW, TripSurveyUploadJob.SURVEY_ZIP_FILE_PREFIX + ".csv")
        );
        assertEquals(TypeFormTripSurveyApiResponseTest.getExpectedCsv(), fileContents);
    }

    private static TypeFormTripSurveyApiResponse createSurveyApiResponse() {
        TypeFormTripSurveyResponse response1 = makeResponse();
        TypeFormTripSurveyResponse response2 = makeResponse();
        TypeFormTripSurveyApiResponse apiResponse = new TypeFormTripSurveyApiResponse();
        apiResponse.items = List.of(response1, response2);
        apiResponse.isTest = true;
        return apiResponse;
    }
}

