package org.opentripplanner.middleware.trip_monitor.jobs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.deleteMonitoredTripAndJourney;
import static org.opentripplanner.middleware.trip_monitor.jobs.CheckMonitoredTrip.shouldSkipMonitoredTripCheck;

/**
 * This class contains tests for the {@link CheckMonitoredTrip} job.
 */
public class CheckMonitoredTripTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTripTest.class);
    private static OtpUser user;

    /**
     * The mockResponse contains an itinerary with a request with the following request parameters:
     * - arriveBy: false
     * - date: 2020-06-09 (a Tuesday)
     * - desired start time: 08:35
     * - itinerary start time: 08:40:10
     * - fromPlace: 1709 NW Irving St, Portland 97209::45.527817334203,-122.68865964147231
     * - toPlace: Uncharted Realities, SW 3rd Ave, Downtown - Portland 97204::45.51639151281627,-122.67681483620306
     * - first itinerary end time: 8:58:44am
     */
    private static final String mockResponse = FileUtils.getFileContents(
        "src/test/resources/org/opentripplanner/middleware/persistence/planResponse.json"
    );
    private static final OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse(mockResponse);

    @BeforeAll
    public static void setup() {
        user = createUser("user@example.com");
    }

    @AfterAll
    public static void tearDown() {
        Persistence.otpUsers.removeById(user.id);
        for (MonitoredTrip trip : Persistence.monitoredTrips.getFiltered(eq("userId", user.id))) {
            deleteMonitoredTripAndJourney(trip);
        }
    }

    /**
     * To run this trip, change the env.yml config values for OTP_SERVER
     * (and OTP_PLAN_ENDPOINT) to a valid OTP server.
     */
    @Test
    public void canMonitorTrip() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        // Submit a query to the OTP server.
        // From P&R to Downtown Orlando
        OtpDispatcherResponse otpDispatcherResponse = OtpDispatcher.sendOtpPlanRequest(
            "28.45119,-81.36818",
            "28.54834,-81.37745"
        );
        // Construct a monitored trip from it.
        MonitoredTrip monitoredTrip = new MonitoredTrip(otpDispatcherResponse)
            .updateAllDaysOfWeek(true);
        monitoredTrip.userId = user.id;
        monitoredTrip.tripName = "My Morning Commute";
        Persistence.monitoredTrips.create(monitoredTrip);
        // Clone the original response and modify some of the elements in order to trigger some of the monitor checks.
        OtpDispatcherResponse simulatedResponse = otpDispatcherResponse.clone();
        Response otpResponse = simulatedResponse.getResponse();
        Itinerary simulatedItinerary = otpResponse.plan.itineraries.get(0);
        // Add fake alerts to simulated itinerary.
        ArrayList<LocalizedAlert> fakeAlerts = new ArrayList<>();
        fakeAlerts.add(new LocalizedAlert());
        simulatedItinerary.legs.get(1).alerts = fakeAlerts;
        simulatedResponse.setResponse(otpResponse);
        LOG.info("Created trip {}", monitoredTrip.id);
        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, simulatedResponse);
        checkMonitoredTrip.run();
        // Assert that there is one notification generated during check.
        // TODO: Improve assertions to use snapshots.
        Assertions.assertEquals(checkMonitoredTrip.notifications.size(), 1);
        // Clear the created trip.
        Persistence.monitoredTrips.removeById(monitoredTrip.id);
    }

    @Test
    public void willGenerateDepartureDelayNotification() {
        MonitoredTrip monitoredTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        OtpDispatcherResponse simulatedResponse = otpDispatcherResponse.clone();
        Itinerary simulatedItinerary = simulatedResponse.getResponse().plan.itineraries.get(0);
        // Set departure time to twenty minutes (in seconds). Default departure time variance threshold is 15 minutes.
        simulatedItinerary.legs.get(0).departureDelay = 60 * 20;
        // Run isolated departure time check for simulated itinerary.
        TripMonitorNotification notification = CheckMonitoredTrip.checkTripForDepartureDelay(monitoredTrip, simulatedItinerary);
        LOG.info("Departure delay notification: {}", notification.body);
        Assertions.assertNotNull(notification);
    }

    @Test
    public void willSkipDepartureDelayNotification() {
        MonitoredTrip monitoredTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        OtpDispatcherResponse simulatedResponse = otpDispatcherResponse.clone();
        Itinerary simulatedItinerary = simulatedResponse.getResponse().plan.itineraries.get(0);
        // Set departure time to ten minutes (in seconds). Default departure time variance threshold is 15 minutes.
        simulatedItinerary.legs.get(0).departureDelay = 60 * 10;
        // Run isolated departure time check for simulated itinerary.
        TripMonitorNotification notification = CheckMonitoredTrip.checkTripForDepartureDelay(monitoredTrip, simulatedItinerary);
        LOG.info("Departure delay notification (should be null): {}", notification);
        Assertions.assertNull(notification);
    }

    /**
     * Run a parameterized test to check if the shouldSkipMonitoredTripCheck works properly for the test cases generated
     * in the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method.
     */
    @ParameterizedTest
    @MethodSource("createSkipTripTestCases")
    void testSkipMonitoredTripCheck(ShouldSkipTripTestCase testCase) {
        DateTimeUtils.useFixedClockAt(testCase.mockTime);
        try {
            assertEquals(testCase.shouldSkipTrip, shouldSkipMonitoredTripCheck(testCase.trip), testCase.message);
        } finally {
            DateTimeUtils.useSystemDefaultClockAndTimezone();
        }
    }

    private static List<ShouldSkipTripTestCase> createSkipTripTestCases() {
        List<ShouldSkipTripTestCase> testCases = new ArrayList<>();

        // June 10, 2020 (Wednesday)
        LocalDateTime exemplarDate = DateTimeUtils.nowAsLocalDateTime().withYear(2020).withMonth(6).withDayOfMonth(10);

        // - Return true for weekend trip when current time is on a weekday.
        MonitoredTrip weekendTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        weekendTrip.saturday = true;
        weekendTrip.sunday = true;
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for a weekend trip when current time is on a weekday",
            exemplarDate, // June 10, 2020 (Wednesday)
            true,
            weekendTrip
        ));

        // - Return true for weekday trip when current time is on a weekend.
        MonitoredTrip weekdayTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        weekdayTrip.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for weekday trip when current time is on a weekend",
            exemplarDate.withDayOfMonth(13), // June 13, 2020 (Saturday)
            true,
            weekdayTrip
        ));

        // - Return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago
        MonitoredTrip laterTodayTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip.updateWeekdays(true);
        // last checked at 1am
        setLastCheckedTimeForTripJourneyState(laterTodayTrip, exemplarDate.withHour(1).withMinute(0));
        testCases.add(new ShouldSkipTripTestCase(
            "should return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago",
            exemplarDate.withHour(3).withMinute(0), // 3am
            false,
            laterTodayTrip
        ));

        // - Return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago
        MonitoredTrip laterTodayTrip2 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip2.updateWeekdays(true);
        // last checked at 2:58am
        setLastCheckedTimeForTripJourneyState(laterTodayTrip2, exemplarDate.withHour(2).withMinute(58));
        testCases.add(new ShouldSkipTripTestCase(
            "should return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago",
            exemplarDate.withHour(3).withMinute(0), // 3am
            true,
            laterTodayTrip2
        ));

        // - Return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        MonitoredTrip laterTodayTrip3 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip3.updateWeekdays(true);
        // last checked at 7:35am
        setLastCheckedTimeForTripJourneyState(laterTodayTrip3, exemplarDate.withHour(7).withMinute(35));
        testCases.add(new ShouldSkipTripTestCase(
            "should return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago",
            exemplarDate.withHour(7).withMinute(55), // 7:55am
            false,
            laterTodayTrip3
        ));

        // - Return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago
        MonitoredTrip laterTodayTrip4 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip4.updateWeekdays(true);
        // last checked at 7:53am
        setLastCheckedTimeForTripJourneyState(laterTodayTrip4, exemplarDate.withHour(7).withMinute(53));
        testCases.add(new ShouldSkipTripTestCase(
            "should return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago",
            exemplarDate.withHour(7).withMinute(55), // 7:55am
            true,
            laterTodayTrip4
        ));

        // - Return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago
        // The monitored trip lead time is 30 minutes, so check every minute
        MonitoredTrip laterTodayTrip5 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip5.updateWeekdays(true);
        // last checked at 8:23am
        setLastCheckedTimeForTripJourneyState(laterTodayTrip5, exemplarDate.withHour(8).withMinute(28));
        testCases.add(new ShouldSkipTripTestCase(
            "should return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago",
            exemplarDate.withHour(8).withMinute(30), // 8:30am
            false,
            laterTodayTrip5
        ));

        // - Return true if trip has started 3 minutes ago
        MonitoredTrip tripThatJustStarted = createMonitoredTrip(user.id, otpDispatcherResponse);
        tripThatJustStarted.updateWeekdays(true);
        // last checked at 8:39am
        setLastCheckedTimeForTripJourneyState(tripThatJustStarted, exemplarDate.withHour(8).withMinute(39));
        testCases.add(new ShouldSkipTripTestCase(
            "should return true if trip has started 3 minutes ago",
            exemplarDate.withHour(8).withMinute(43), // 8:43am
            true,
            tripThatJustStarted
        ));

        return testCases;
    }

    private static void setLastCheckedTimeForTripJourneyState(MonitoredTrip trip, LocalDateTime dateTime) {
        JourneyState journeyState = trip.retrieveJourneyState();
        journeyState.lastChecked = Timestamp.valueOf(dateTime).getTime();
        Persistence.journeyStates.replace(journeyState.id, journeyState);
    }

    private static class ShouldSkipTripTestCase {
        /* a helpful message describing the particular test case */
        public final String message;
        /* The time to mock for this test case */
        public final LocalDateTime mockTime;
        /**
         * if true, it is expected that the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method should
         * calculate that the given trip should be skipped.
         */
        public final boolean shouldSkipTrip;
        /**
         * The trip for the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method to calculate whether
         * skipping trip analysis should occur.
         */
        public final MonitoredTrip trip;

        private ShouldSkipTripTestCase(
            String message, LocalDateTime mockTime, boolean shouldSkipTrip, MonitoredTrip trip
        ) {
            this.message = message;
            this.mockTime = mockTime;
            this.shouldSkipTrip = shouldSkipTrip;
            this.trip = trip;
        }
    }
}
