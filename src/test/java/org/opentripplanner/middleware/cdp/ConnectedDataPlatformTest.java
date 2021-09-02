package org.opentripplanner.middleware.cdp;

import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpMethod;
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

import java.io.File;
import java.time.LocalDate;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.cdp.ConnectedDataManager.getFileName;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDateMinusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDatePlusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getEndOfDay;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfDay;
import static org.opentripplanner.middleware.utils.FileUtils.getContentsOfFileInZip;

public class ConnectedDataPlatformTest extends OtpMiddlewareTestEnvironment {

    private TripRequest tripRequest;
    private TripSummary tripSummary;
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
            FileUtils.deleteFile(new File(tempFile));
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
    }
    /**
     * Make sure that the first upload is created and contains the correct upload date.
     */
    @Test
    public void canStageFirstUpload() {
        ConnectedDataManager.stageUploadDays();
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
        ConnectedDataManager.stageUploadDays();
        ConnectedDataManager.processTripHistory();
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
        String contents = ConnectedDataManager.getAnonymizedTripHistory(startOfYesterday, getEndOfDay(startOfYesterday));
        assertEquals(fileContents, contents);
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
        Date startOfYesterday = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        tripRequest = PersistenceTestUtils.createTripRequest(userId, startOfYesterday);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequest.id, startOfYesterday);
        ConnectedDataManager.stageUploadDays();
        ConnectedDataManager.processTripHistory();
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
        assertTrue(tripHistory.tripRequests.stream().anyMatch(tripRequest -> tripRequest.userId.equals(userId)));
        ConnectedDataManager.removeUsersTripHistory(userId);
        // Trip request and summary are removed as part of the 'removeUsersTripHistory' method. Setting these to null
        // prevents a delete error under the tidy-up process.
        tripRequest = null;
        tripSummary = null;
        fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(startOfYesterday, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        tripHistory = JsonUtils.getPOJOFromJSON(fileContents, TripHistory.class);
        assertTrue(tripHistory.tripRequests.stream().noneMatch(tripRequest -> tripRequest.userId.equals(userId)));
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
        ConnectedDataManager.stageUploadDays();
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
        Date tenDaysInThePast = getStartOfDay(getDateMinusNumberOfDays(new Date(), 10));
        tripRequest = PersistenceTestUtils.createTripRequest(otpUser.id, tenDaysInThePast);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequest.id, tenDaysInThePast);

        // Update 'storeTripHistory' value.
        otpUser.storeTripHistory = false;
        mockAuthenticatedRequest(
            String.format("%s/%s", OTP_USER_PATH, otpUser.id),
            JsonUtils.toJson(otpUser),
            otpUser,
            HttpMethod.PUT
        );
        assertEquals(
            1,
            Persistence.tripHistoryUploads.getCountFiltered(Filters.eq("uploadDate", tenDaysInThePast))
        );

        // Trip request and summary are removed as part of the 'removeUsersTripHistory' method. Setting these to null
        // prevents a delete error under the tidy-up process.
        tripRequest = null;
        tripSummary = null;

        // Set the zip file name and temp file name so they are both removed as part of the tidy-up process.
        zipFileName = getFileName(tenDaysInThePast, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );

    }
}
