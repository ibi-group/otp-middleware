package org.opentripplanner.middleware.tripMonitor.jobs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.deleteMonitoredTripAndJourney;
import static org.opentripplanner.middleware.tripMonitor.jobs.CheckMonitoredTrip.generateTripPlanQueryParams;
import static org.opentripplanner.middleware.tripMonitor.jobs.CheckMonitoredTrip.shouldSkipMonitoredTripCheck;

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
    private static String mockResponse;
    private static OtpDispatcherResponse otpDispatcherResponse;

    @BeforeAll
    public static void setup() throws IOException {
        user = createUser("user@example.com");
        mockResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "persistence/planResponse.json"
        );
        otpDispatcherResponse = new OtpDispatcherResponse(mockResponse);
    }

    @AfterAll
    public static void tearDown() {
        Persistence.otpUsers.removeById(user.id);
        for (MonitoredTrip trip : Persistence.monitoredTrips.getFiltered(eq("userId", user.id))) {
            deleteMonitoredTripAndJourney(trip);
        }
    }

    /**
     * To run this trip, change the env.yml config values for OTP_API_ROOT
     * (and OTP_PLAN_ENDPOINT) to a valid OTP server.
     */
    @Test
    public void canMonitorTrip() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        MonitoredTrip monitoredTrip = TestUtils.constructedMonitoredTripFromOtpResponse();
        monitoredTrip.updateAllDaysOfWeek(true);
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
        deleteMonitoredTripAndJourney(monitoredTrip);
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
     * Run a parameterized test to check if the {@link CheckMonitoredTrip#shouldSkipMonitoredTripCheck) works properly
     * for the test cases generated in the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method.
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

        // June 10, 2020 (Wednesday) at noon
        ZonedDateTime noon10June2020 = DateTimeUtils.nowAsZonedDateTime(ZoneId.of("America/Los_Angeles"))
            .withYear(2020)
            .withMonth(6)
            .withDayOfMonth(10)
            .withHour(12)
            .withMinute(0);

        // - Return true for weekend trip when current time is on a weekday.
        MonitoredTrip weekendTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        weekendTrip.saturday = true;
        weekendTrip.sunday = true;
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for a weekend trip when current time is on a weekday",
            noon10June2020, // mock time: June 10, 2020 (Wednesday)
            true,
            weekendTrip
        ));

        // - Return true for weekday trip when current time is on a weekend.
        MonitoredTrip weekdayTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        weekdayTrip.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for weekday trip when current time is on a weekend",
            noon10June2020.withDayOfMonth(13), // mock time: June 13, 2020 (Saturday)
            true,
            weekdayTrip
        ));

        // - Return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago
        MonitoredTrip laterTodayTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            noon10June2020.withHour(1).withMinute(0), // last checked at 1am
            "should return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago",
            noon10June2020.withHour(3).withMinute(0), // mock time: 3am
            false,
            laterTodayTrip
        ));

        // - Return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago
        MonitoredTrip laterTodayTrip2 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip2.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            noon10June2020.withHour(2).withMinute(58), // last checked at 2:58am
            "should return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago",
            noon10June2020.withHour(3).withMinute(0), // mock time: 3am
            true,
            laterTodayTrip2
        ));

        // - Return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        MonitoredTrip laterTodayTrip3 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip3.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            noon10June2020.withHour(7).withMinute(35), // last checked at 7:35am
            "should return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago",
            noon10June2020.withHour(7).withMinute(55), // mock time: 7:55am
            false,
            laterTodayTrip3
        ));

        // - Return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago
        MonitoredTrip laterTodayTrip4 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip4.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            noon10June2020.withHour(7).withMinute(53), // last checked at 7:53am
            "should return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago",
            noon10June2020.withHour(7).withMinute(55), // mock time: 7:55am
            true,
            laterTodayTrip4
        ));

        // - Return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago
        // The monitored trip lead time is 30 minutes, so check every minute
        MonitoredTrip laterTodayTrip5 = createMonitoredTrip(user.id, otpDispatcherResponse);
        laterTodayTrip5.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            noon10June2020.withHour(8).withMinute(28), // last checked at 8:23am
            "should return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago",
            noon10June2020.withHour(8).withMinute(30), // mock time: 8:30am
            false,
            laterTodayTrip5
        ));

        // - Return true if trip has started 3 minutes ago
        MonitoredTrip tripThatJustStarted = createMonitoredTrip(user.id, otpDispatcherResponse);
        tripThatJustStarted.updateWeekdays(true);
        testCases.add(new ShouldSkipTripTestCase(
            noon10June2020.withHour(8).withMinute(39), // last checked at 8:39am
            "should return true if trip has started 3 minutes ago",
            noon10June2020.withHour(8).withMinute(43), // mock time: 8:43am
            true,
            tripThatJustStarted
        ));

        return testCases;
    }

    private static class ShouldSkipTripTestCase {
        /* a helpful message describing the particular test case */
        public final String message;
        /* The time to mock for this test case */
        public final ZonedDateTime mockTime;
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

        // Constructor for test case for a trip that has yet to be monitored (ie the mocked time will be the first time
        // the trip is checked).
        private ShouldSkipTripTestCase(
            String message, ZonedDateTime mockTime, boolean shouldSkipTrip, MonitoredTrip trip
        ) {
            this(null, message, mockTime, shouldSkipTrip, trip);
        }

        // Constructor for test case that will set the JourneyState with the last checked time as needed.
        private ShouldSkipTripTestCase(
            ZonedDateTime lastCheckedTime,
            String message,
            ZonedDateTime mockTime,
            boolean shouldSkipTrip,
            MonitoredTrip trip
        ) {
            this.message = message;
            this.mockTime = mockTime;
            this.shouldSkipTrip = shouldSkipTrip;
            if (lastCheckedTime != null) {
                JourneyState journeyState = trip.retrieveJourneyState();
                journeyState.lastChecked = lastCheckedTime.toInstant().toEpochMilli();
                Persistence.journeyStates.replace(journeyState.id, journeyState);
            }
            this.trip = trip;
        }
    }

    /**
     * Run a parameterized test to make sure the {@link CheckMonitoredTrip#generateTripPlanQueryParams} method
     * generates correct query params for the test cases generated in the
     * {@link CheckMonitoredTripTest#createQueryParamsTestCases()} method.
     */
    @ParameterizedTest
    @MethodSource("createQueryParamsTestCases")
    @Disabled // TODO remove decorator once test cases have been added
    public void canGenerateTripPlanQueryParams(QueryParamsTestCase testCase) throws URISyntaxException {
        DateTimeUtils.useFixedClockAt(testCase.mockTime);
        try {
            assertEquals(testCase.expectedQueryParams, generateTripPlanQueryParams(testCase.trip), testCase.message);
        } finally {
            DateTimeUtils.useSystemDefaultClockAndTimezone();
        }
    }

    private static List<QueryParamsTestCase> createQueryParamsTestCases() {
        List<QueryParamsTestCase> testCases = new ArrayList<>();

        // TODO implement below test cases (minus the daylight savings tests)

        // should use current date for a trip saved today

        // should use the current date (current date is Wednesday) for a weekday depart at trip starting at noon that
        // was originally planned on a Friday

        // should use the current date (current date is Wednesday) for a weekday depart at trip that has a requested
        // start time of 11:55pm, but has an itinerary start time at 12:01am on Thursday and was originally planned on a
        // Friday

        // should use tomorrow's date (current date is Wednesday) for a weekday depart at trip that has a requested
        // start time of 12:01am, but has an itinerary start time at 12:30am on Thursday and was originally planned on
        // a Friday

        // should use the current date (current date is Wednesday) for a weekday arrive by trip starting at noon that
        // has an itinerary duration of 40 minutes that was originally planned on a Friday

        // should use the tomorrow's date (current date is Wednesday) for a weekday arrive by trip that is requested to
        // arrive by 12:01am that was originally planned on a Friday

        // should use the tomorrow's date (current date is Wednesday) for a weekday arrive by trip that is requested to
        // arrive by 1:01am that has an itinerary duration of 40 minutes that was originally planned on a Friday

        // TODO dream up of some daylight savings time test cases

        return testCases;
    }

    private static class QueryParamsTestCase {
        /* a helpful message describing the particular test case */
        public final String message;
        /**
         * the expected string that the {@link CheckMonitoredTrip#generateTripPlanQueryParams} method should
         * generate for the given trip.
         */
        public final String expectedQueryParams;
        /* The time to mock for this test case */
        public final ZonedDateTime mockTime;
        /**
         * The trip for the {@link CheckMonitoredTrip#generateTripPlanQueryParams} method to generate OTP query params
         * for
         */
        public final MonitoredTrip trip;

        private QueryParamsTestCase(
            String message, String expectedQueryParams, ZonedDateTime mockTime, MonitoredTrip trip
        ) {
            this.message = message;
            this.expectedQueryParams = expectedQueryParams;
            this.mockTime = mockTime;
            this.trip = trip;
        }
    }
}
