package org.opentripplanner.middleware.connecteddataplatform;

import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.S3Utils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.zenika.snapshotmatcher.SnapshotMatcher.matchesSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.getFileName;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDateMinusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDatePlusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfDay;
import static org.opentripplanner.middleware.utils.FileUtils.getContentsOfFileInZip;

public class ConnectedDataPlatformTest extends OtpMiddlewareTestEnvironment {

    private static List<TripRequest> tripRequests;
    private TripRequest tripRequest;
    private TripSummary tripSummary;
    private static TripRequest tripRequestRemovedByTest;
    private static TripSummary tripSummaryRemovedByTest;
    private String tempFile;
    private String zipFileName;
    private static OtpUser otpUser;
    private static final String OTP_USER_PATH = "api/secure/user";

    @BeforeAll
    public static void setUp() {
        setAuthDisabled(true);
        OtpTestUtils.mockOtpServer();
    }

    @AfterEach
    public void afterEach() throws Exception {
        if (tripRequest != null) {
            Persistence.tripRequests.removeById(tripRequest.id);
            tripRequest = null;
        }
        if (tripSummary != null) {
            Persistence.tripSummaries.removeById(tripSummary.id);
            tripSummary = null;
        }
        for(TripHistoryUpload tripHistoryUpload : Persistence.tripHistoryUploads.getAll()) {
            Persistence.tripHistoryUploads.removeById(tripHistoryUpload.id);
        }
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

    @AfterAll
    public static void tearDown() {
        setAuthDisabled(false);
        if (otpUser != null) {
            Persistence.otpUsers.removeById(otpUser.id);
        }

        // Both these entities should be removed as part of testing, but if the test(s) fails, make sure they are
        // removed.
        if (tripRequestRemovedByTest != null &&
            Persistence.tripRequests.getById(tripRequestRemovedByTest.id) != null
        ) {
            Persistence.tripRequests.removeById(tripRequestRemovedByTest.id);
        }
        if (tripSummaryRemovedByTest != null &&
            Persistence.tripSummaries.getById(tripSummaryRemovedByTest.id) != null
        ) {
            Persistence.tripSummaries.removeById(tripSummaryRemovedByTest.id);
        }

        if (tripRequests != null) {
            tripRequests.forEach(tripRequest -> Persistence.tripRequests.removeById(tripRequest.id));
        }
    }

    /**
     * Make sure that the first upload is created and contains the correct upload date.
     */
    @Test
    public void canStageFirstUpload() {
        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUpload tripHistoryUpload = TripHistoryUpload.getFirst();
        Date startOfDay = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        assertNotNull(tripHistoryUpload);
        assertEquals(startOfDay.getTime(), tripHistoryUpload.uploadDate.getTime());
    }

    /**
     * Confirm that a single zip file is created which contains a single JSON file. Also confirm that the contents
     * written to the JSON file is correct and covers a single day's worth of trip data.
     */
    @Test
    public void canCreateZipFileWithContent() throws Exception {
        assumeTrue(IS_END_TO_END);
        ConnectedDataManager.IS_TEST = true;

        String userId = UUID.randomUUID().toString();
        Date startOfYesterday = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        tripRequest = PersistenceTestUtils.createTripRequest(userId, startOfYesterday);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequest.id, startOfYesterday);
        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUploadJob.processTripHistory();
        zipFileName = getFileName(startOfYesterday, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(startOfYesterday, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        MatcherAssert.assertThat(fileContents, matchesSnapshot());
    }

    /**
     * Create a user with trip data and confirm this is written to file. Then remove this user's trip data and confirm
     * the file is overwritten minus the user's trip data.
     */
    @Test
    public void canRemoveUsersTripDataFromFile() throws Exception {
        assumeTrue(IS_END_TO_END);
        ConnectedDataManager.IS_TEST = true;

        String userId = UUID.randomUUID().toString();
        String batchId = UUID.randomUUID().toString();
        Date startOfYesterday = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        tripRequestRemovedByTest = PersistenceTestUtils.createTripRequest(userId, batchId, startOfYesterday);
        tripSummaryRemovedByTest = PersistenceTestUtils.createTripSummary(tripRequestRemovedByTest.id, startOfYesterday);

        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUploadJob.processTripHistory();
        zipFileName = getFileName(startOfYesterday, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(startOfYesterday, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        TripHistory tripHistory = JsonUtils.getPOJOFromJSON(fileContents, TripHistory.class);
        // Confirm that the user's trip request saved to file contains the expected batch id.
        assertTrue(tripHistory.tripRequests.stream().anyMatch(tripRequest -> tripRequest.batchId.equals(batchId)));

        ConnectedDataManager.removeUsersTripHistory(userId);
        TripHistoryUploadJob.processTripHistory();
        fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(startOfYesterday, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        tripHistory = JsonUtils.getPOJOFromJSON(fileContents, TripHistory.class);
        // Confirm that once the user's trip data has been removed the file contents does not contain any trip requests
        // matching the related batch id.
        assertTrue(tripHistory.tripRequests.stream().noneMatch(tripRequest -> tripRequest.batchId.equals(batchId)));
    }

    /**
     * If the system is down for a period of time, make sure that the days between the last upload and the current day
     * are correctly staged.
     */
    @Test
    public void canCorrectlyStageDays() {
        Date sevenDaysAgo = getStartOfDay(getDateMinusNumberOfDays(new Date(), 7));
        Set<LocalDate> betweenDays = DateTimeUtils.getDatesBetween(
            getDatePlusNumberOfDays(sevenDaysAgo,1),
            new Date()
        );
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(sevenDaysAgo);
        Persistence.tripHistoryUploads.create(tripHistoryUpload);
        TripHistoryUploadJob.stageUploadDays();
        assertEquals(
            betweenDays.size(),
            Persistence.tripHistoryUploads.getCountFiltered(Filters.gt("uploadDate", sevenDaysAgo))
        );
    }

    /**
     * Add an OTP user with 'storeTripHistory' set to true and related trip requests/summaries. Via the API update the
     * 'storeTripHistory' to false and confirm that the trip history is removed and the appropriate days are flagged for
     * updating.
     */
    @Test
    public void canRemoveTripHistoryViaAPI() throws Exception {
        assumeTrue(IS_END_TO_END);
        ConnectedDataManager.IS_TEST = true;

        // Set back stop. This allows dates after this to trigger an upload.
        Date twentyDaysInThePast = getStartOfDay(getDateMinusNumberOfDays(new Date(), 20));
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(twentyDaysInThePast);
        tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        // Create OTP user and trip data.
        otpUser = PersistenceTestUtils.createUser("test@example.com");
        Date oneDayInThePast = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        tripRequestRemovedByTest = PersistenceTestUtils.createTripRequest(otpUser.id, oneDayInThePast);
        tripSummaryRemovedByTest = PersistenceTestUtils.createTripSummary(tripRequestRemovedByTest.id, oneDayInThePast);

        // Update 'storeTripHistory' value.
        otpUser.storeTripHistory = false;
        mockAuthenticatedRequest(
            String.format("%s/%s", OTP_USER_PATH, otpUser.id),
            JsonUtils.toJson(otpUser),
            otpUser,
            HttpMethod.PUT
        );
        // Only expecting one trip history upload entry matching the date the trip request and summary were made.
        assertEquals(
            1,
            Persistence.tripHistoryUploads.getCountFiltered(Filters.eq("uploadDate", oneDayInThePast))
        );

        // Set the zip file name and temp file name so they are both removed as part of the tidy-up process.
        zipFileName = getFileName(oneDayInThePast, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
    }

    /**
     * Confirm that the correct number of trip requests are written to file. This is primarily to test streaming trip
     * requests to file and that none are missed.
     */
    @Test
    public void canStreamTheCorrectNumberOfTripRequest() throws IOException {
        assumeTrue(IS_END_TO_END);
        ConnectedDataManager.IS_TEST = true;
        String userId = UUID.randomUUID().toString();

        // Set back stop. This allows dates after this to trigger an upload.
        Date twentyDaysInThePast = getStartOfDay(getDateMinusNumberOfDays(new Date(), 20));
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(twentyDaysInThePast);
        tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        // Create trip requests for required date.
        Date yesterday = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        tripRequests = PersistenceTestUtils.createTripRequests(15, userId, yesterday);

        // Create trip history upload for required date.
        tripHistoryUpload = new TripHistoryUpload(yesterday);
        tripHistoryUpload.status = TripHistoryUploadStatus.PENDING.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        TripHistoryUploadJob.processTripHistory();
        zipFileName = getFileName(yesterday, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );

        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(yesterday, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        MatcherAssert.assertThat(fileContents, matchesSnapshot());
    }
}
