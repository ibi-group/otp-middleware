package org.opentripplanner.middleware.connecteddataplatform;

import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.S3Utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.zenika.snapshotmatcher.SnapshotMatcher.matchesSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.getFileName;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedRequest;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getPreviousWholeHourFrom;
import static org.opentripplanner.middleware.utils.FileUtils.getContentsOfFileInZip;

public class ConnectedDataPlatformTest extends OtpMiddlewareTestEnvironment {

    private static final List<TripRequest> tripRequests = new ArrayList<>();
    private static final List<TripSummary> tripSummaries = new ArrayList<>();
    private TripRequest tripRequest;
    private TripSummary tripSummary;
    private static TripRequest tripRequestRemovedByTest;
    private static TripSummary tripSummaryRemovedByTest;
    private String tempFile;
    private String zipFileName;
    private static OtpUser otpUser;
    private static final String OTP_USER_PATH = "api/secure/user";
    private static final LocalDateTime PREVIOUS_WHOLE_HOUR_FROM_NOW = getPreviousWholeHourFrom(LocalDateTime.now());

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
    void canStageFirstUpload() {
        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUpload tripHistoryUpload = TripHistoryUpload.getFirst();
        assertNotNull(tripHistoryUpload);
        assertTrue(PREVIOUS_WHOLE_HOUR_FROM_NOW.isEqual(tripHistoryUpload.uploadHour));
    }

    /**
     * Confirm that a single zip file is created which contains a single JSON file. Also confirm that the contents
     * written to the JSON file is correct and covers a single hour's worth of trip data and that the correct lat/log
     * have been randomized.
     */
    @Test
    void canCreateZipFileWithContent() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "783726";
        tripRequest = PersistenceTestUtils.createTripRequest(userId, batchId, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequest.id, batchId, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        MatcherAssert.assertThat(fileContents, matchesSnapshot());

        // Confirm that all non transit lat/lon's have been randomized (with test lat/lon).
        List<AnonymizedTripRequest> anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        assertNotNull(anonymizedTripRequests);
        assertNull(anonymizedTripRequests.get(0).fromPlace);
        assertNull(anonymizedTripRequests.get(0).toPlace);
        anonymizedTripRequests.get(0).itineraries.forEach(intin -> {
            intin.legs.forEach(leg -> {
                if (leg.transitLeg) {
                    assertNotNull(leg.from);
                    assertNotNull(leg.to);
                } else {
                    assertNull(leg.from);
                    assertNull(leg.to);
                }
            });
        });
    }

    /**
     * Confirm that a single zip file is created which contains a single JSON file. Also confirm that the contents
     * written to the JSON file is correct and includes no itineraries and an error message.
     */
    @Test
    void canCreateZipFileForTripSummaryWithError() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "783726";
        tripRequest = PersistenceTestUtils.createTripRequest(userId, batchId, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripSummary = PersistenceTestUtils.createTripSummaryWithError(tripRequest.id, batchId, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        MatcherAssert.assertThat(fileContents, matchesSnapshot());

        // Confirm that all non transit lat/lon's have been randomized (with test lat/lon).
        List<AnonymizedTripRequest> anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        assertNotNull(anonymizedTripRequests);
    }


    /**
     * Confirm that the trip request with the most modes is used.
     */
    @Test
    void canCreateContentWithTripRequestWithMaxModes() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "12345678";
        String mode = "WALK,BUS,RAIL,TRAM";
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(
            userId,
            batchId,
            DateTimeUtils.convertToDate(PREVIOUS_WHOLE_HOUR_FROM_NOW),
            mode
        );
        TripRequest tripRequestTwo = PersistenceTestUtils.createTripRequest(
            userId,
            batchId,
            DateTimeUtils.convertToDate(PREVIOUS_WHOLE_HOUR_FROM_NOW),
            "WALK"
        );
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripRequests.add(tripRequestTwo);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchId, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        // Confirm that all non transit lat/lon's have been randomized (with test lat/lon).
        List<AnonymizedTripRequest> anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        assertNotNull(anonymizedTripRequests);
        assertEquals(mode, String.join(",", anonymizedTripRequests.get(0).mode));
    }

    /**
     * Create a user with trip data and confirm this is written to file. Then remove this user's trip data and confirm
     * the file is overwritten minus the user's trip data.
     */
    @Test
    void canRemoveUsersTripDataFromFile() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userIdOne = UUID.randomUUID().toString();
        String batchIdOne = "2222222222";
        tripRequestRemovedByTest = PersistenceTestUtils.createTripRequest(userIdOne, batchIdOne, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripSummaryRemovedByTest = PersistenceTestUtils.createTripSummary(tripRequestRemovedByTest.id, batchIdOne, PREVIOUS_WHOLE_HOUR_FROM_NOW);

        // Additional trip request and summary which should remain after the first user's trip data is removed.
        String userIdTwo = UUID.randomUUID().toString();
        String batchIdTwo = "777777777";
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(userIdTwo, batchIdTwo, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchIdTwo, PREVIOUS_WHOLE_HOUR_FROM_NOW);

        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        List<AnonymizedTripRequest> anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        // Confirm that the user's trip request saved to file contains the expected batch ids.
        assertNotNull(anonymizedTripRequests);
        assertTrue(anonymizedTripRequests.stream().anyMatch(anonymizedTripRequest -> anonymizedTripRequest.requestId.equals(batchIdOne)));
        assertTrue(anonymizedTripRequests.stream().anyMatch(anonymizedTripRequest -> anonymizedTripRequest.requestId.equals(batchIdTwo)));

        ConnectedDataManager.removeUsersTripHistory(userIdOne);
        TripHistoryUploadJob.processTripHistory(true);
        fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        assertNotNull(anonymizedTripRequests);
        // Confirm that once the user's trip data has been removed the file contents only the second batch id.
        assertFalse(anonymizedTripRequests.stream().anyMatch(anonymizedTripRequest -> anonymizedTripRequest.requestId.equals(batchIdOne)));
        assertTrue(anonymizedTripRequests.stream().anyMatch(anonymizedTripRequest -> anonymizedTripRequest.requestId.equals(batchIdTwo)));
    }

    /**
     * If the system is down for a period of time, make sure that the hours between the last upload and the current hour
     * are correctly staged.
     */
    @Test
    void canCorrectlyStageHours() {
        LocalDateTime sevenHoursAgo = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(7);
        List<LocalDateTime> betweenHours = DateTimeUtils.getHoursBetween(sevenHoursAgo, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(sevenHoursAgo);
        Persistence.tripHistoryUploads.create(tripHistoryUpload);
        TripHistoryUploadJob.stageUploadHours();
        assertEquals(
            betweenHours.size() + 1, // plus one for an hour ago.
            Persistence.tripHistoryUploads.getCountFiltered(Filters.gt("uploadHour", sevenHoursAgo))
        );
    }

    /**
     * If the system is down for a period of time, make sure that the days between the last upload and the current day
     * are correctly staged.
     */
    @Test
    void canCorrectlyStageDays() {
        LocalDateTime fourDaysAgo = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(4);
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(fourDaysAgo);
        Persistence.tripHistoryUploads.create(tripHistoryUpload);
        TripHistoryUploadJob.stageUploadDays();
        assertEquals(
            1, // If system is down, it will only upload the previous day.
            Persistence.tripHistoryUploads.getCountFiltered(Filters.gt("uploadHour", fourDaysAgo))
        );
    }

    /**
     * Add an OTP user with 'storeTripHistory' set to true and related trip requests/summaries. Via the API update the
     * 'storeTripHistory' to false and confirm that the trip history is removed and the appropriate days are flagged for
     * updating.
     */
    @Test
    void canRemoveTripHistoryViaAPI() throws Exception {
        assumeTrue(IS_END_TO_END);

        // Set backstop. This allows dates after this to trigger an upload.
        LocalDateTime twentyHoursInThePast = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(20);
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(twentyHoursInThePast);
        tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        // Create OTP user and trip data.
        otpUser = PersistenceTestUtils.createUser("test@example.com");
        tripRequestRemovedByTest = PersistenceTestUtils.createTripRequest(otpUser.id, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripSummaryRemovedByTest = PersistenceTestUtils.createTripSummary(tripRequestRemovedByTest.id, PREVIOUS_WHOLE_HOUR_FROM_NOW);

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
            Persistence.tripHistoryUploads.getCountFiltered(Filters.eq("uploadHour", PREVIOUS_WHOLE_HOUR_FROM_NOW))
        );
    }

    /**
     * Confirm that the correct number of trip requests and related summaries are written to file.
     */
    @Test
    void canStreamTheCorrectNumberOfTrips() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchIdOne = "99999999";
        String batchIdTwo = "11111111";

        // Create required trip requests and add to list for deletion once the test has completed.
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(userId, batchIdOne, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        TripRequest tripRequestTwo = PersistenceTestUtils.createTripRequest(userId, batchIdTwo, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripRequests.add(tripRequestTwo);

        // Create required trip summaries and add to list for deletion once the test has completed.
        tripSummaries.clear();
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchIdOne, PREVIOUS_WHOLE_HOUR_FROM_NOW));
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchIdOne, PREVIOUS_WHOLE_HOUR_FROM_NOW));
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestTwo.id, batchIdTwo, PREVIOUS_WHOLE_HOUR_FROM_NOW));
        tripSummaries.add(PersistenceTestUtils.createTripSummary(tripRequestTwo.id, batchIdTwo, PREVIOUS_WHOLE_HOUR_FROM_NOW));

        // Set backstop. This allows dates after this to trigger an upload.
        LocalDateTime twentyHoursInThePast = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(20);
        TripHistoryUpload tripHistoryUpload = new TripHistoryUpload(twentyHoursInThePast);
        tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        // Create trip history upload for required date.
        tripHistoryUpload = new TripHistoryUpload(PREVIOUS_WHOLE_HOUR_FROM_NOW);
        tripHistoryUpload.status = TripHistoryUploadStatus.PENDING.getValue();
        Persistence.tripHistoryUploads.create(tripHistoryUpload);

        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );

        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        MatcherAssert.assertThat(fileContents, matchesSnapshot());
    }

    @Test
    void shouldProcessTripHistory() {
        assertTrue(ConnectedDataManager.shouldProcessTripHistory("some-bucket-name"));
        assertFalse(ConnectedDataManager.shouldProcessTripHistory(null));
        assertFalse(ConnectedDataManager.shouldProcessTripHistory(""));
    }

    @Test
    void canHandleMissingPlaceCoordinates() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "missing-coords";
        String mode = "WALK,BUS,RAIL,TRAM";
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(
            userId,
            batchId,
            DateTimeUtils.convertToDate(PREVIOUS_WHOLE_HOUR_FROM_NOW),
            mode
        );

        // Remove coordinates from trip request and update.
        tripRequestOne.fromPlace = "Airport, Stansted, Essex, England :: ";
        tripRequestOne.toPlace = "Airport, Glasgow Airport, Glasgow, Scotland :: ";
        Persistence.tripRequests.replace(tripRequestOne.id, tripRequestOne);
        tripRequests.clear();
        tripRequests.add(tripRequestOne);

        OtpResponse planResponse = OtpTestUtils.OTP_DISPATCHER_PLAN_RESPONSE.getResponse();
        for (Itinerary itinerary : planResponse.plan.itineraries) {
            for (Leg leg : itinerary.legs) {
                // Set all legs to transit so that the coordinates are extracted.
                leg.transitLeg = true;
            }
        }
        tripSummary = new TripSummary(planResponse.plan, planResponse.error, tripRequestOne.id, batchId);
        Persistence.tripSummaries.create(tripSummary);

        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        List<AnonymizedTripRequest> anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        assertNotNull(anonymizedTripRequests);
        // Confirm that all missing lat/lon's have the default value of 0.
        assertEquals(0, anonymizedTripRequests.get(0).fromPlace.lat);
        assertEquals(0, anonymizedTripRequests.get(0).fromPlace.lon);
        assertEquals(0, anonymizedTripRequests.get(0).toPlace.lat);
        assertEquals(0, anonymizedTripRequests.get(0).toPlace.lon);
    }

    @Test
    void canHandleMissingModes() throws Exception {
        assumeTrue(IS_END_TO_END);

        String userId = UUID.randomUUID().toString();
        String batchId = "missing-modes";
        TripRequest tripRequestOne = PersistenceTestUtils.createTripRequest(
            userId,
            batchId,
            DateTimeUtils.convertToDate(PREVIOUS_WHOLE_HOUR_FROM_NOW),
            null,
            false
        );
        tripRequests.clear();
        tripRequests.add(tripRequestOne);
        tripSummary = PersistenceTestUtils.createTripSummary(tripRequestOne.id, batchId, PREVIOUS_WHOLE_HOUR_FROM_NOW);
        TripHistoryUploadJob.stageUploadHours();
        TripHistoryUploadJob.processTripHistory(true);
        zipFileName = getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.ZIP_FILE_NAME_SUFFIX);
        tempFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            zipFileName
        );
        String fileContents = getContentsOfFileInZip(
            tempFile,
            getFileName(PREVIOUS_WHOLE_HOUR_FROM_NOW, ConnectedDataManager.DATA_FILE_NAME_SUFFIX)
        );
        List<AnonymizedTripRequest> anonymizedTripRequests = JsonUtils.getPOJOFromJSONAsList(fileContents, AnonymizedTripRequest.class);
        assertNotNull(anonymizedTripRequests);
        // Confirm that no modes are included in the anonymized trip request.
        assertEquals("", anonymizedTripRequests.get(0).mode.get(0));
    }

    @ParameterizedTest
    @MethodSource("createWeeklyMondaySundayFolderNameCases")
    void canGetWeeklyMondaySundayFolderName(int day, String expected) {
        LocalDate date = LocalDate.of(2024, 9, day);
        assertEquals(expected, ConnectedDataManager.getWeeklyMondaySundayFolderName("PMD", date));
    }

    private static Stream<Arguments> createWeeklyMondaySundayFolderNameCases() {
        return Stream.of(
            Arguments.of(6, "PMD_2024-09-02_2024-09-08"),
            Arguments.of(7, "PMD_2024-09-02_2024-09-08"),
            Arguments.of(8, "PMD_2024-09-02_2024-09-08"),
            Arguments.of(9, "PMD_2024-09-09_2024-09-15"),
            Arguments.of(10, "PMD_2024-09-09_2024-09-15"),
            Arguments.of(11, "PMD_2024-09-09_2024-09-15"),
            Arguments.of(12, "PMD_2024-09-09_2024-09-15"),
            Arguments.of(13, "PMD_2024-09-09_2024-09-15")
        );
    }
}
