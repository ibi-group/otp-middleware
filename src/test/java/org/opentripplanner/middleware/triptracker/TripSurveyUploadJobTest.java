package org.opentripplanner.middleware.triptracker;

import jersey.repackaged.com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.connecteddataplatform.IntervalUploadStatus;
import org.opentripplanner.middleware.models.IntervalUpload;
import org.opentripplanner.middleware.models.TripSurveyUpload;
import org.opentripplanner.middleware.models.typeform.Responses;
import org.opentripplanner.middleware.models.typeform.ResponsesTest;
import org.opentripplanner.middleware.models.typeform.Response;
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
import static org.opentripplanner.middleware.models.typeform.ResponseTest.makeResponse;
import static org.opentripplanner.middleware.models.typeform.ResponsesTest.EXPECTED_HEADER;
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

        surveyUploadTwoDaysAgo = new TripSurveyUpload("upload-2", twoDaysAgo, IntervalUploadStatus.PENDING);
        surveyUploadThreeDaysAgo = new TripSurveyUpload("upload-3", threeDaysAgo, IntervalUploadStatus.COMPLETED);

        surveyUploads = Lists.newArrayList(surveyUploadTwoDaysAgo, surveyUploadThreeDaysAgo);

        Persistence.tripSurveyUploads.create(surveyUploadTwoDaysAgo);
        Persistence.tripSurveyUploads.create(surveyUploadThreeDaysAgo);
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
        // Just for this test, delete the previous days.
        Persistence.tripSurveyUploads.removeById(surveyUploadTwoDaysAgo.id);
        Persistence.tripSurveyUploads.removeById(surveyUploadThreeDaysAgo.id);

        TripSurveyUploadJob job = new TripSurveyUploadJob();
        job.stageUploadDays();
        TripSurveyUpload upload = IntervalUpload.getFirstUpload(Persistence.tripSurveyUploads);
        assertNotNull(upload);
        assertTrue(PREVIOUS_WHOLE_DAY_FROM_NOW.isEqual(upload.uploadHour));
    }

    @Test
    void canRunJob() {
        assumeTrue(IS_END_TO_END);

        // After running the job, assuming that API calls were successful,
        // the upload records should be updated.

        TripSurveyUploadJob job = new TripSurveyUploadJob(localDateTime -> createSurveyApiResponse(), () -> EXPECTED_HEADER);
        job.run();

        TripSurveyUpload upload = job.getLastUploadCreated();
        assertNotNull(upload);
        assertTrue(PREVIOUS_WHOLE_DAY_FROM_NOW.isEqual(upload.uploadHour));
        assertEquals(IntervalUploadStatus.COMPLETED, upload.status);
    }

    @Test
    void canMakeResponsesParams() {
        LocalDateTime date = LocalDateTime.of(2024, 10, 25, 0, 0);

        String s = TripSurveyUploadJob.responsesParams(date);

        // Using epoch timestamps to avoid conversions from local to UTC time which TypeForm requires.
        assertEquals("?page_size=1000&since=1729839600&until=1729925999", s);
    }

    /**
     * Confirm that a single zip file is created which contains the compiled survey responses (CSV). Also confirm that the contents
     * written to the CSV file is correct and covers a single day's worth of responses.
     */
    @Test
    void canCreateZipFileWithContent() throws Exception {
        assumeTrue(IS_END_TO_END);

        Responses apiResponse = createSurveyApiResponse();

        TripSurveyUploadJob job = new TripSurveyUploadJob(localDateTime -> apiResponse, () -> EXPECTED_HEADER);
        job.stageUploadDays();
        assertTrue(job.processSurveyHistory(job.getLastUploadCreated(), apiResponse));
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
        assertEquals(ResponsesTest.getExpectedCsv(), fileContents);
    }

    private static Responses createSurveyApiResponse() {
        Response response1 = makeResponse();
        Response response2 = makeResponse();
        Responses apiResponse = new Responses();
        apiResponse.items = List.of(response1, response2);
        apiResponse.isTest = true;
        return apiResponse;
    }
}

