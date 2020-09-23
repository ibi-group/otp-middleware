package org.opentripplanner.middleware.tripMonitor.jobs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.TestUtils.isEndToEnd;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.deleteMonitoredTripAndJourney;
import static org.opentripplanner.middleware.utils.ConfigUtils.isRunningCi;

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
        TestUtils.mockOtpServer();
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

    @AfterEach
    public void tearDownAfterTest() {
        TestUtils.resetOtpMocks();
    }

    /**
     * To run this trip, change the env.yml config values for OTP_SERVER
     * (and OTP_PLAN_ENDPOINT) to a valid OTP server.
     */
    @Test
    public void canMonitorTrip() throws URISyntaxException {
        // Do not run this test on Travis CI because it requires a live OTP server
        // FIXME: Add live otp server to e2e tests.
        assumeTrue(!isRunningCi && isEndToEnd);
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
        OtpResponse otpResponse = simulatedResponse.getResponse();
        Itinerary simulatedItinerary = otpResponse.plan.itineraries.get(0);
        // Add fake alerts to simulated itinerary.
        ArrayList<LocalizedAlert> fakeAlerts = new ArrayList<>();
        fakeAlerts.add(new LocalizedAlert());
        simulatedItinerary.legs.get(1).alerts = fakeAlerts;
        simulatedResponse.setResponse(otpResponse);
        LOG.info("Created trip {}", monitoredTrip.id);
        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip);
        checkMonitoredTrip.run();
        // Assert that there is one notification generated during check.
        // TODO: Improve assertions to use snapshots.
        Assertions.assertEquals(checkMonitoredTrip.notifications.size(), 1);
        // Clear the created trip.
        Persistence.monitoredTrips.removeById(monitoredTrip.id);
    }

    @Test
    public void willGenerateDepartureDelayNotification() throws URISyntaxException {
        MonitoredTrip monitoredTrip = createMonitoredTrip(user.id, otpDispatcherResponse, true);
        OtpDispatcherResponse simulatedResponse = otpDispatcherResponse.clone();
        Itinerary simulatedItinerary = simulatedResponse.getResponse().plan.itineraries.get(0);
        // Set departure time to twenty minutes (in seconds). Default departure time variance threshold is 15 minutes.
        simulatedItinerary.legs.get(0).departureDelay = 60 * 20;

        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip);
        // Set isolated departure time check for simulated itinerary.
        checkMonitoredTrip.matchingItinerary = simulatedItinerary;
        TripMonitorNotification notification = checkMonitoredTrip.checkTripForDepartureDelay();
        LOG.info("Departure delay notification: {}", notification.body);
        Assertions.assertNotNull(notification);
    }

    @Test
    public void willSkipDepartureDelayNotification() throws URISyntaxException {
        MonitoredTrip monitoredTrip = createMonitoredTrip(user.id, otpDispatcherResponse, true);
        OtpDispatcherResponse simulatedResponse = otpDispatcherResponse.clone();
        Itinerary simulatedItinerary = simulatedResponse.getResponse().plan.itineraries.get(0);
        // Set departure time to ten minutes (in seconds). Default departure time variance threshold is 15 minutes.
        simulatedItinerary.legs.get(0).departureDelay = 60 * 10;

        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip);
        // Run isolated departure time check for simulated itinerary.
        checkMonitoredTrip.matchingItinerary = simulatedItinerary;
        TripMonitorNotification notification = checkMonitoredTrip.checkTripForDepartureDelay();
        LOG.info("Departure delay notification (should be null): {}", notification);
        Assertions.assertNull(notification);
    }

    /**
     * Run a parameterized test to check if the {@link CheckMonitoredTrip#shouldSkipMonitoredTripCheck) works properly
     * for the test cases generated in the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method.
     */
    @ParameterizedTest
    @MethodSource("createSkipTripTestCases")
    void testSkipMonitoredTripCheck(ShouldSkipTripTestCase testCase) throws Exception {
        DateTimeUtils.useFixedClockAt(testCase.mockTime);

        // create a mock OTP response for planning a trip on a weekday target datetime
        OtpResponse mockWeekdayResponse = otpDispatcherResponse.getResponse();
        Itinerary mockWeekdayItinerary = mockWeekdayResponse.plan.itineraries.get(0);
        updateBaseItineraryTime(
            mockWeekdayItinerary,
            testCase.mockTime.withYear(2020).withMonth(6).withDayOfMonth(8).withHour(8).withMinute(40).withSecond(10)
        );

        // set mocks to a list containing just the weekday response if no mocks are provided
        TestUtils.setupOtpMocks(testCase.otpMocks == null ? List.of(mockWeekdayResponse) : testCase.otpMocks);

        // create these entries in the database at this point to ensure the correct mocked time is set
        MonitoredTrip trip = testCase.trip;
        // if trip is null, create the default weekday trip
        if (trip == null) {
            trip = createMonitoredTrip(user.id, otpDispatcherResponse);
        }

        // if last checked time is not null, there is an assumption that the journey state has been created before.
        // Therefore, create a mock journey state and set the matching itinerary to the first itinerary in the first
        // otp mock or the mockWeekdayItinerary if no mocks are provided
        if (testCase.lastCheckedTime != null) {
            JourneyState journeyState = trip.retrieveJourneyState();
            if (testCase.useOtpMockWhenCreatingJourneyState && testCase.otpMocks.size() > 0) {
                journeyState.matchingItinerary = testCase.otpMocks.get(0).plan.itineraries.get(0);
            } else {
                journeyState.matchingItinerary = mockWeekdayItinerary;
            }
            journeyState.targetDate = "2020-06-08";
            journeyState.lastCheckedMillis = testCase.lastCheckedTime.toInstant().toEpochMilli();
            Persistence.journeyStates.replace(journeyState.id, journeyState);
        }
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(trip);
        try {
            assertEquals(testCase.shouldSkipTrip, checkMonitoredTrip.shouldSkipMonitoredTripCheck(), testCase.message);
        } finally {
            DateTimeUtils.useSystemDefaultClockAndTimezone();
        }
    }

    private static List<ShouldSkipTripTestCase> createSkipTripTestCases() throws URISyntaxException {
        List<ShouldSkipTripTestCase> testCases = new ArrayList<>();

        // June 8, 2020 (Monday) at noon
        ZonedDateTime noonMonday8June2020 = DateTimeUtils.nowAsZonedDateTime(ZoneId.of("America/Los_Angeles"))
            .withYear(2020)
            .withMonth(6)
            .withDayOfMonth(8)
            .withHour(12)
            .withMinute(0);

        // - Return true for weekend trip when current time is on a weekday.
        MonitoredTrip weekendTrip = createMonitoredTrip(user.id, otpDispatcherResponse, true);
        weekendTrip.updateAllDaysOfWeek(false);
        weekendTrip.saturday = true;
        weekendTrip.sunday = true;
        // create a mock OTP response for planning a trip on the next weekend target datetime
        OtpResponse mockWeekendResponse = otpDispatcherResponse.getResponse();
        Itinerary mockWeekendItinerary = mockWeekendResponse.plan.itineraries.get(0);
        updateBaseItineraryTime(
            mockWeekendItinerary,
            noonMonday8June2020.withDayOfMonth(13).withHour(8).withMinute(40).withSecond(10)
        );
        List<OtpResponse> weekendTripOtpMocks = List.of(mockWeekendResponse);

        testCases.add(new ShouldSkipTripTestCase(
            "should return true for a weekend trip when current time is on a weekday",
            noonMonday8June2020, // mock time: June 10, 2020 (Wednesday)
            weekendTripOtpMocks,
            true,
            weekendTrip
        ));

        // - Return true for weekday trip when current time is on a weekend.
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for weekday trip when current time is on a weekend",
            noonMonday8June2020.withDayOfMonth(6), // mock time: June 6, 2020 (Saturday)
            true
        ));

        // - Return true if trip is starting today, but before lead time
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(2).withMinute(0), // last checked at 2am
            "should return true if trip is starting today, but current time is before lead time",
            noonMonday8June2020.withHour(3).withMinute(0), // mock time: 3am,
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            true
        ));

        // - Return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(4).withMinute(0), // last checked at 4am
            "should return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago",
            noonMonday8June2020.withHour(6).withMinute(0), // mock time: 6am
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            false
        ));

        // - Return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(2).withMinute(58), // last checked at 2:58am
            "should return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago",
            noonMonday8June2020.withHour(3).withMinute(0), // mock time: 3am
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            true
        ));

        // - Return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(7).withMinute(35), // last checked at 7:35am
            "should return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago",
            noonMonday8June2020.withHour(7).withMinute(55), // mock time: 7:55am
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            false
        ));

        // - Return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(7).withMinute(53), // last checked at 7:53am
            "should return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago",
            noonMonday8June2020.withHour(7).withMinute(55), // mock time: 7:55am
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            true
        ));

        // - Return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(8).withMinute(28), // last checked at 8:23am
            "should return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago",
            noonMonday8June2020.withHour(8).withMinute(30), // mock time: 8:30am
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            false
        ));

        // - Returns false if trip still hadn't ended prior to the last checked time even though the current time is
        //   after the last known end time of the trip
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(8).withMinute(58).withSecond(0), // last checked at 8:58am
            "should return false if trip hadn't ended the last time it was checked despite it being after the last known end time",
            noonMonday8June2020.withHour(8).withMinute(59), // mock time: 8:59am
            Collections.EMPTY_LIST, // no mocks because OTP request occurs after skip check
            false
        ));

        // - Return true if trip has ended as of the last check 3 minutes ago
        // create a mock OTP response for planning a trip on the next weekday target datetime
        OtpResponse mockNextWeekdayResponse = otpDispatcherResponse.getResponse();
        Itinerary mockNextWeekdayItinerary = mockNextWeekdayResponse.plan.itineraries.get(0);
        updateBaseItineraryTime(
            mockNextWeekdayItinerary,
            noonMonday8June2020.withYear(2020).withMonth(6).withDayOfMonth(9).withHour(8).withMinute(40).withSecond(10)
        );
        testCases.add(new ShouldSkipTripTestCase(
            noonMonday8June2020.withHour(8).withMinute(59), // last checked at 8:59am
            "should return true if trip has ended as of the last check",
            noonMonday8June2020.withHour(9).withMinute(00), // mock time: 9:00am
            List.of(mockNextWeekdayResponse), // add mock for checking the next possible trip the following day
            true,
            null,
            false
        ));

        return testCases;
    }

    private static class ShouldSkipTripTestCase {
        /* The last time a journey was checked */
        public final ZonedDateTime lastCheckedTime;

        /* a helpful message describing the particular test case */
        public final String message;

        /* The time to mock */
        public final ZonedDateTime mockTime;

        /* A list of mock responses to return from the mock OTP server */
        public final List<OtpResponse> otpMocks;

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

        /**
         * if false, the mock journey state that gets created will use the default weekday itinerary. Otherwise the
         * journey state will have it's matching itinerary set to the first itinerary of the first otp response in the
         * first otp mock.
         */
        public final boolean useOtpMockWhenCreatingJourneyState;

        // Constructor for test case for a trip that has yet to be monitored (ie the mocked time will be the first time
        // the trip is checked).
        private ShouldSkipTripTestCase(
            String message,
            ZonedDateTime mockTime,
            List<OtpResponse> otpMocks,
            boolean shouldSkipTrip,
            MonitoredTrip trip
        ) throws URISyntaxException {
            this(null, message, mockTime, otpMocks, shouldSkipTrip, trip, true);
        }

        // Constructor for test case with the default weekday trip
        public ShouldSkipTripTestCase(
            String message,
            ZonedDateTime mockTime,
            boolean shouldSkipTrip
        ) throws URISyntaxException {
            this(null, message, mockTime, null, shouldSkipTrip, null, true);
        }

        public ShouldSkipTripTestCase(
            ZonedDateTime lastCheckedTime,
            String message,
            ZonedDateTime mockTime,
            boolean shouldSkipTrip
        ) throws URISyntaxException {
            this(lastCheckedTime, message, mockTime, null, shouldSkipTrip, null, true);
        }

        public ShouldSkipTripTestCase(
            ZonedDateTime lastCheckedTime,
            String message,
            ZonedDateTime mockTime,
            List<OtpResponse> otpMocks,
            boolean shouldSkipTrip
        ) throws URISyntaxException {
            this(lastCheckedTime, message, mockTime, otpMocks, shouldSkipTrip, null, true);
        }

        // Constructor for test case that will set the JourneyState with the last checked time as needed.
        private ShouldSkipTripTestCase(
            ZonedDateTime lastCheckedTime,
            String message,
            ZonedDateTime mockTime,
            List<OtpResponse> otpMocks,
            boolean shouldSkipTrip,
            MonitoredTrip trip,
            boolean useOtpMockWhenCreatingJourneyState
        ) throws URISyntaxException {
            this.lastCheckedTime = lastCheckedTime;
            this.message = message;
            this.mockTime = mockTime;
            this.otpMocks = otpMocks;
            this.shouldSkipTrip = shouldSkipTrip;
            this.trip = trip;
            this.useOtpMockWhenCreatingJourneyState = useOtpMockWhenCreatingJourneyState;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    /**
     * Offsets all times in the given itinerary relative to the given base time. The base time is assumed to be the new
     * start time for the itinerary. Whatever the offset from the initial itinerary's start time and the new start time
     * will be the offset that is applied to all other times in the itinerary.
     */
    private static void updateBaseItineraryTime(Itinerary mockItinerary, ZonedDateTime baseZonedDateTime) {
        long offset = baseZonedDateTime.toEpochSecond() * 1000 - mockItinerary.startTime.getTime();
        mockItinerary.startTime = new Date(mockItinerary.startTime.getTime() + offset);
        mockItinerary.endTime = new Date(mockItinerary.endTime.getTime() + offset);
        for (Leg leg : mockItinerary.legs) {
            leg.startTime = new Date(leg.startTime.getTime() + offset);
            leg.endTime = new Date(leg.endTime.getTime() + offset);
        }
    }
}
