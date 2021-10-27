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
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.LatLongUtils;
import org.opentripplanner.middleware.utils.S3Utils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.zenika.snapshotmatcher.SnapshotMatcher.matchesSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.getFileName;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.utils.FileUtils.getContentsOfFileInZip;

public class ConnectedDataPlatformTest extends OtpMiddlewareTestEnvironment {

    private static List<TripRequest> tripRequests = new ArrayList<>();
    private static List<TripSummary> tripSummaries = new ArrayList<>();
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
        for (TripHistoryUpload tripHistoryUpload : Persistence.tripHistoryUploads.getAll()) {
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
        tripRequests.forEach(tripRequest -> Persistence.tripRequests.removeById(tripRequest.id));
        tripRequests.clear();
        tripSummaries.forEach(tripSummary1 -> Persistence.tripSummaries.removeById(tripSummary1.id));
        tripSummaries.clear();
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
    }

    /**
     * Make sure that the first upload is created and contains the correct upload date.
     */
    @Test
    public void canStageFirstUpload() {
        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUpload tripHistoryUpload = TripHistoryUpload.getFirst();
        LocalDate startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();
        assertNotNull(tripHistoryUpload);
        assertTrue(startOfYesterday.isEqual(tripHistoryUpload.uploadDate));
    }

    /**
     * Confirm that a single zip file is created which contains a single JSON file. Also confirm that the contents
     * written to the JSON file is correct and covers a single day's worth of trip data and that the correct lat/log
     * have been randomized.
     */
    @Test
    public void canCreateZipFileWithContent() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "783726";
        LocalDate startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();
        tripRequest = PersistenceTestUtils.createTripRequest(userId, batchId, startOfYesterday);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequest.id, batchId, startOfYesterday);
        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUploadJob.processTripHistory(true);
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

        // Confirm that all non transit lat/lon's have been randomized (with test lat/lon).
        List<AnonymizedTrip> anonymizedTrips = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTrip.class);
        assertNotNull(anonymizedTrips);
        Coordinates testCoordinates = new Coordinates(LatLongUtils.TEST_LAT, LatLongUtils.TEST_LON);
        assertEquals(testCoordinates, anonymizedTrips.get(0).tripRequest.fromPlace);
        assertEquals(testCoordinates, anonymizedTrips.get(0).tripRequest.toPlace);
        anonymizedTrips.get(0).tripSummaries.forEach(tripSummary -> {
            tripSummary.tripPlan.itineraries.forEach(intin -> {
                intin.legs.forEach(leg -> {
                    if (leg.transitLeg) {
                        assertNotEquals(testCoordinates, leg.from.coordinates);
                        assertNotEquals(testCoordinates, leg.to.coordinates);
                    } else {
                        assertEquals(testCoordinates, leg.from.coordinates);
                        assertEquals(testCoordinates, leg.to.coordinates);
                    }
                });
            });
        });
    }

    /**
     * Confirm that a single zip file is created which contains a single JSON file. Also confirm that the contents
     * written to the JSON file is correct and includes no itineraries and an error message.
     */
    @Test
    public void canCreateZipFileForTripSummaryWithError() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "783726";
        LocalDate startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();
        tripRequest = PersistenceTestUtils.createTripRequest(userId, batchId, startOfYesterday);
        tripSummary = PersistenceTestUtils.createTripSummaryWithError(tripRequest.id, batchId, startOfYesterday);
        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUploadJob.processTripHistory(true);
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

        // Confirm that all non transit lat/lon's have been randomized (with test lat/lon).
        List<AnonymizedTrip> anonymizedTrips = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTrip.class);
        assertNotNull(anonymizedTrips);
    }


    /**
     * Confirm that the trip request with the most modes is used.
     */
    @Test
    public void canCreateContentWithTripRequestWithMaxModes() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "12345678";
        String mode = "WALK%2CBUS%2CRAIL%2CTRAM";
        LocalDate startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(
            userId,
            batchId,
            DateTimeUtils.convertToDate(startOfYesterday),
            mode
        );
        TripRequest tripRequestTwo = PersistenceTestUtils.createTripRequest(
            userId,
            batchId,
            DateTimeUtils.convertToDate(startOfYesterday),
            "WALK"
        );
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripRequests.add(tripRequestTwo);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchId, startOfYesterday);
        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUploadJob.processTripHistory(true);
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
        // Confirm that all non transit lat/lon's have been randomized (with test lat/lon).
        List<AnonymizedTrip> anonymizedTrips = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTrip.class);
        assertNotNull(anonymizedTrips);
        assertEquals(mode.replaceAll("%2C",","), anonymizedTrips.get(0).tripRequest.mode);
    }

    /**
     * Create a user with trip data and confirm this is written to file. Then remove this user's trip data and confirm
     * the file is overwritten minus the user's trip data.
     */
    @Test
    public void canRemoveUsersTripDataFromFile() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userIdOne = UUID.randomUUID().toString();
        String batchIdOne = "2222222222";
        LocalDate startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();
        tripRequestRemovedByTest = PersistenceTestUtils.createTripRequest(userIdOne, batchIdOne, startOfYesterday);
        tripSummaryRemovedByTest = PersistenceTestUtils.createTripSummary(tripRequestRemovedByTest.id, batchIdOne, startOfYesterday);

        // Additional trip request and summary which should remain after the first user's trip data is removed.
        String userIdTwo = UUID.randomUUID().toString();
        String batchIdTwo = "777777777";
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(userIdTwo, batchIdTwo, startOfYesterday);
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchIdTwo, startOfYesterday);

        TripHistoryUploadJob.stageUploadDays();
        TripHistoryUploadJob.processTripHistory(true);
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
        List<AnonymizedTrip> anonymizedTrips = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTrip.class);
        // Confirm that the user's trip request saved to file contains the expected batch ids.
        assertNotNull(anonymizedTrips);
        assertTrue(anonymizedTrips.stream().anyMatch(anonymizedTrip -> anonymizedTrip.tripRequest.batchId.equals(batchIdOne)));
        assertTrue(anonymizedTrips.stream().anyMatch(anonymizedTrip -> anonymizedTrip.tripRequest.batchId.equals(batchIdTwo)));

        ConnectedDataManager.removeUsersTripHistory(userIdOne);
        TripHistoryUploadJob.processTripHistory(true);
        fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(startOfYesterday, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        anonymizedTrips = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTrip.class);
        assertNotNull(anonymizedTrips);
        // Confirm that once the user's trip data has been removed the file contents only the second batch id.
        assertFalse(anonymizedTrips.stream().anyMatch(anonymizedTrip -> anonymizedTrip.tripRequest.batchId.equals(batchIdOne)));
        assertTrue(anonymizedTrips.stream().anyMatch(anonymizedTrip -> anonymizedTrip.tripRequest.batchId.equals(batchIdTwo)));
    }

    /**
     * If the system is down for a period of time, make sure that the days between the last upload and the current day
     * are correctly staged.
     */
    @Test
    public void canCorrectlyStageDays() {
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        LocalDate sixDaysAgo = LocalDate.now().minusDays(6);
        Set<LocalDate> betweenDays = DateTimeUtils.getDatesBetween(sixDaysAgo, LocalDate.now());
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

        // Set back stop. This allows dates after this to trigger an upload.
        LocalDate twentyDaysInThePast = LocalDate.now().minusDays(20).atStartOfDay().toLocalDate();
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(twentyDaysInThePast);
        tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        // Create OTP user and trip data.
        otpUser = PersistenceTestUtils.createUser("test@example.com");
        LocalDate oneDayInThePast = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();
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
    }

    /**
     * Confirm that the correct number of trip requests and related summaries are written to file.
     */
    @Test
    public void canStreamTheCorrectNumberOfTrips() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchIdOne = "99999999";
        String batchIdTwo = "11111111";
        LocalDate startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay().toLocalDate();

        // Create required trip requests and add to list for deletion once the test has completed.
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(userId, batchIdOne, startOfYesterday);
        TripRequest tripRequestTwo = PersistenceTestUtils.createTripRequest(userId, batchIdTwo, startOfYesterday);
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripRequests.add(tripRequestTwo);

        // Create required trip summaries and add to list for deletion once the test has completed.
        tripSummaries.clear();
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchIdOne, startOfYesterday));
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchIdOne, startOfYesterday));
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestTwo.id, batchIdTwo, startOfYesterday));
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestTwo.id, batchIdTwo, startOfYesterday));

        // Set back stop. This allows dates after this to trigger an upload.
        LocalDate twentyDaysInThePast = LocalDate.now().minusDays(20).atStartOfDay().toLocalDate();
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(twentyDaysInThePast);
        tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        // Create trip history upload for required date.
        tripHistoryUpload = new TripHistoryUpload(startOfYesterday);
        tripHistoryUpload.status = TripHistoryUploadStatus.PENDING.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        TripHistoryUploadJob.processTripHistory(true);
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
     * Test to confirm that the coordinates provided for randomizing do not match those returned.
     */
    @Test
    public void canRandomizeLatLon() {
        Coordinates coordinates = new Coordinates(33.64070037704429,-84.44622866991179);
        Coordinates randomized = LatLongUtils.getRandomizedCoordinates(coordinates);
        assertNotEquals(coordinates, randomized);
    }
}
