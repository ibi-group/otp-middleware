package org.opentripplanner.middleware.tripMonitor.jobs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.testUtils.CommonTestUtils;
import org.opentripplanner.middleware.testUtils.OtpTestUtils;
import org.opentripplanner.middleware.tripMonitor.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
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
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.testUtils.CommonTestUtils.isEndToEnd;
import static org.opentripplanner.middleware.testUtils.OtpTestUtils.DEFAULT_PLAN_URI;
import static org.opentripplanner.middleware.testUtils.PersistenceTestUtils.createMonitoredTrip;
import static org.opentripplanner.middleware.testUtils.PersistenceTestUtils.createUser;
import static org.opentripplanner.middleware.testUtils.PersistenceTestUtils.deleteMonitoredTrip;
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
        OtpTestUtils.mockOtpServer();
        user = createUser("user@example.com");
        mockResponse = CommonTestUtils.getResourceFileContentsAsString(
            "otp/response/planResponse.json"
        );
        otpDispatcherResponse = new OtpDispatcherResponse(mockResponse, DEFAULT_PLAN_URI);
    }

    @AfterAll
    public static void tearDown() {
        Persistence.otpUsers.removeById(user.id);
        for (MonitoredTrip trip : Persistence.monitoredTrips.getFiltered(eq("userId", user.id))) {
            deleteMonitoredTrip(trip);
        }
    }

    @AfterEach
    public void tearDownAfterTest() {
        OtpTestUtils.resetOtpMocks();
    }

    /**
     * To run this trip, change the env.yml config values for OTP_API_ROOT
     * (and OTP_PLAN_ENDPOINT) to a valid OTP server.
     */
    @Test
    public void canMonitorTrip() throws URISyntaxException {
        // Do not run this test on Travis CI because it requires a live OTP server
        // FIXME: Add live otp server to e2e tests.
        assumeTrue(!isRunningCi && isEndToEnd);
        MonitoredTrip monitoredTrip = new MonitoredTrip(OtpTestUtils.sendSamplePlanRequest());
        monitoredTrip.updateAllDaysOfWeek(true);
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
        Assertions.assertEquals(1, checkMonitoredTrip.notifications.size());
        // Clear the created trip.
        deleteMonitoredTrip(monitoredTrip);
    }

    @ParameterizedTest
    @MethodSource("createDelayNotificationTestCases")
    void testDelayNotifications(DelayNotificationTestCase testCase) {
        TripMonitorNotification notification = testCase.checkMonitoredTrip.checkTripForDelay(testCase.delayType);
        if (testCase.expectedNotificationMessage == null) {
            assertNull(notification, testCase.message);
        } else {
            assertNotNull(notification, String.format("Expected notification for test case: %s", testCase.message));
            assertEquals(testCase.expectedNotificationMessage, notification.body, testCase.message);
        }
    }

    private static List<DelayNotificationTestCase> createDelayNotificationTestCases () throws URISyntaxException {
        List<DelayNotificationTestCase> testCases = new ArrayList<>();

        // should not create departure/arrival notification for on-time trip
        CheckMonitoredTrip onTimeTrip = createCheckMonitoredTrip();
        onTimeTrip.trip.journeyState = createDefaultJourneyState();
        testCases.add(new DelayNotificationTestCase(
            onTimeTrip,
            NotificationType.DEPARTURE_DELAY,
            "should not create departure notification for on-time trip"
        ));
        testCases.add(new DelayNotificationTestCase(
            onTimeTrip,
            NotificationType.ARRIVAL_DELAY,
            "should not create arrival notification for on-time trip"
        ));

        // should create a departure notification for 20 minute late trip
        CheckMonitoredTrip twentyMinutesLateTimeTrip = createCheckMonitoredTrip();
        offsetItineraryTime(
            twentyMinutesLateTimeTrip.matchingItinerary,
            TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES)
        );
        twentyMinutesLateTimeTrip.trip.journeyState = createDefaultJourneyState();
        testCases.add(new DelayNotificationTestCase(
            twentyMinutesLateTimeTrip,
            NotificationType.DEPARTURE_DELAY,
            "Your trip is now predicted to depart 20 minutes late (at 09:00).",
            "should create a departure notification for 20 minute late trip"
        ));
        testCases.add(new DelayNotificationTestCase(
            twentyMinutesLateTimeTrip,
            NotificationType.ARRIVAL_DELAY,
            "Your trip is now predicted to arrive 20 minutes late (at 09:18).",
            "should create a arrival notification for 20 minute late trip"
        ));

        // should not create departure notification for 20 minute late trip w/ 15 minute threshold and 18 minute late
        // baseline
        // should not create arrival notification for 20 minute late trip w/ 15 minute threshold and 18 minute late
        // baseline
        CheckMonitoredTrip twentyMinutesLateTripWithUpdatedThreshold = createCheckMonitoredTrip();
        offsetItineraryTime(
            twentyMinutesLateTripWithUpdatedThreshold.matchingItinerary,
            TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES)
        );
        JourneyState twentyMinutesLateJourneyStateWithUpdatedThreshold = createDefaultJourneyState();
        long eighteenMinutesInMilliseconds = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES);
        twentyMinutesLateJourneyStateWithUpdatedThreshold.baselineDepartureTimeEpochMillis +=
            eighteenMinutesInMilliseconds;
        twentyMinutesLateJourneyStateWithUpdatedThreshold.baselineArrivalTimeEpochMillis +=
            eighteenMinutesInMilliseconds;
        twentyMinutesLateTripWithUpdatedThreshold.trip.journeyState = twentyMinutesLateJourneyStateWithUpdatedThreshold;
        testCases.add(new DelayNotificationTestCase(
            twentyMinutesLateTripWithUpdatedThreshold,
            NotificationType.DEPARTURE_DELAY,
            "should not create departure notification for 20 minute late trip w/ 15 minute threshold and 18 minute late baseline"
        ));
        testCases.add(new DelayNotificationTestCase(
            twentyMinutesLateTripWithUpdatedThreshold,
            NotificationType.ARRIVAL_DELAY,
            "should not create arrival notification for 20 minute late trip w/ 15 minute threshold and 18 minute late baseline"
        ));

        // should create a departure notification for on-time trip w/ 20 minute late threshold and 18 minute late baseline
        // should create a arrival notification for on-time trip w/ 20 minute late threshold and 18 minute late baseline
        CheckMonitoredTrip onTimeTripWithUpdatedThreshold = createCheckMonitoredTrip();
        JourneyState onTimeJourneyStateWithUpdatedThreshold = createDefaultJourneyState();
        onTimeJourneyStateWithUpdatedThreshold.baselineDepartureTimeEpochMillis += eighteenMinutesInMilliseconds;
        onTimeJourneyStateWithUpdatedThreshold.baselineArrivalTimeEpochMillis += eighteenMinutesInMilliseconds;
        onTimeTripWithUpdatedThreshold.trip.journeyState = onTimeJourneyStateWithUpdatedThreshold;
        testCases.add(new DelayNotificationTestCase(
            onTimeTripWithUpdatedThreshold,
            NotificationType.DEPARTURE_DELAY,
            "Your trip is now predicted to depart about on time (at 08:40).",
            "should create a departure notification for on-time trip w/ 20 minute late threshold and 18 minute late baseline"
        ));
        testCases.add(new DelayNotificationTestCase(
            onTimeTripWithUpdatedThreshold,
            NotificationType.ARRIVAL_DELAY,
            "Your trip is now predicted to arrive about on time (at 08:58).",
            "should create a arrival notification for on-time trip w/ 20 minute late threshold and 18 minute late baseline"
        ));

        return testCases;
    }

    /**
     * Creates a new CheckMonitoredTrip instance with a new non-persisted MonitoredTrip instance. The monitored trip is
     * created using the default OTP response. Also, creates a new matching itinerary that consists of the first
     * itinerary in the default OTP response.
     */
    private static CheckMonitoredTrip createCheckMonitoredTrip() throws URISyntaxException {
        MonitoredTrip monitoredTrip = createMonitoredTrip(user.id, otpDispatcherResponse, false);
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip);
        checkMonitoredTrip.matchingItinerary = createDefaultItinerary();
        return checkMonitoredTrip;
    }

    private static Itinerary createDefaultItinerary() {
        return otpDispatcherResponse.clone().getResponse().plan.itineraries.get(0);
    }

    private static JourneyState createDefaultJourneyState() {
        JourneyState journeyState = new JourneyState();
        Itinerary defaultItinerary = createDefaultItinerary();
        journeyState.scheduledArrivalTimeEpochMillis = defaultItinerary.endTime.getTime();
        journeyState.scheduledDepartureTimeEpochMillis = defaultItinerary.startTime.getTime();
        journeyState.baselineArrivalTimeEpochMillis = defaultItinerary.endTime.getTime();
        journeyState.baselineDepartureTimeEpochMillis = defaultItinerary.startTime.getTime();
        return journeyState;
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
        OtpTestUtils.setupOtpMocks(testCase.otpMocks == null ? List.of(mockWeekdayResponse) : testCase.otpMocks);

        // create these entries in the database at this point to ensure the correct mocked time is set
        MonitoredTrip trip = testCase.trip;
        // if trip is null, create the default weekday trip
        if (trip == null) {
            trip = createMonitoredTrip(user.id, otpDispatcherResponse, true);
        }

        // if last checked time is not null, there is an assumption that the journey state has been created before.
        // Therefore, create a mock journey state and set the matching itinerary to the first itinerary in the first
        // otp mock or the mockWeekdayItinerary if no mocks are provided
        if (testCase.lastCheckedTime != null) {
            JourneyState journeyState = trip.journeyState;
            if (testCase.useOtpMockWhenCreatingJourneyState && testCase.otpMocks.size() > 0) {
                journeyState.matchingItinerary = testCase.otpMocks.get(0).plan.itineraries.get(0);
            } else {
                journeyState.matchingItinerary = mockWeekdayItinerary;
            }
            journeyState.targetDate = "2020-06-08";
            journeyState.lastCheckedEpochMillis = testCase.lastCheckedTime.toInstant().toEpochMilli();
            Persistence.monitoredTrips.replace(trip.id, trip);
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

    private static class DelayNotificationTestCase {
        /**
         * The trip to use to test. It is assumed that the trip is completely setup with an appropriate journey state.
         */
        public CheckMonitoredTrip checkMonitoredTrip;

        /**
         * Whether the check is for the arrival or departure
         */
        public NotificationType delayType;

        /**
         * The expected body of the notification message. If this is not set, it is assumed in the test case that a
         * notification should not be generated.
         */
        public String expectedNotificationMessage;

        /**
         * Message for test case
         */
        public String message;

        public DelayNotificationTestCase(
            CheckMonitoredTrip checkMonitoredTrip,
            NotificationType delayType,
            String message
        ) {
            this(checkMonitoredTrip, delayType, null, message);
        }

        public DelayNotificationTestCase(
            CheckMonitoredTrip checkMonitoredTrip,
            NotificationType delayType,
            String expectedNotificationMessage,
            String message
        ) {
            this.checkMonitoredTrip = checkMonitoredTrip;
            this.delayType = delayType;
            this.expectedNotificationMessage = expectedNotificationMessage;
            this.message = message;
        }
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
        offsetItineraryTime(
            mockItinerary,
            baseZonedDateTime.toEpochSecond() * 1000 - mockItinerary.startTime.getTime()
        );
    }

    /**
     * Offsets the itinerary's timing by adding the given offset to the overall start/end time and each leg start/end
     * times.
     */
    private static void offsetItineraryTime(Itinerary mockItinerary, long offsetMillis) {
        mockItinerary.startTime = new Date(mockItinerary.startTime.getTime() + offsetMillis);
        mockItinerary.endTime = new Date(mockItinerary.endTime.getTime() + offsetMillis);
        for (Leg leg : mockItinerary.legs) {
            leg.startTime = new Date(leg.startTime.getTime() + offsetMillis);
            leg.endTime = new Date(leg.endTime.getTime() + offsetMillis);
        }
    }
}
