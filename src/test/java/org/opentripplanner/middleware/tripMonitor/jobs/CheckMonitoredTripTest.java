package org.opentripplanner.middleware.tripMonitor.jobs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
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
import org.opentripplanner.middleware.tripMonitor.TripStatus;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    private static final ZonedDateTime noonMonday8June2020 = DateTimeUtils.makeOtpZonedDateTime(new Date())
        .withYear(2020)
        .withMonth(6)
        .withDayOfMonth(8)
        .withHour(12)
        .withMinute(0);

    @BeforeAll
    public static void setup() throws IOException {
        OtpTestUtils.mockOtpServer();
        user = PersistenceTestUtils.createUser("user@example.com");
        mockResponse = CommonTestUtils.getTestResourceAsString(
            "otp/response/planResponse.json"
        );
        otpDispatcherResponse = new OtpDispatcherResponse(mockResponse, OtpTestUtils.DEFAULT_PLAN_URI);
    }

    @AfterAll
    public static void tearDown() {
        Persistence.otpUsers.removeById(user.id);
        for (MonitoredTrip trip : Persistence.monitoredTrips.getFiltered(eq("userId", user.id))) {
            PersistenceTestUtils.deleteMonitoredTrip(trip);
        }
    }

    @AfterEach
    public void tearDownAfterTest() {
        OtpTestUtils.resetOtpMocks();
        DateTimeUtils.useSystemDefaultClockAndTimezone();
    }

    /**
     * To run this trip, change the env.yml config values for OTP_API_ROOT
     * (and OTP_PLAN_ENDPOINT) to a valid OTP server.
     */
    @Test
    public void canMonitorTrip() throws URISyntaxException, CloneNotSupportedException {
        // Do not run this test on Travis CI because it requires a live OTP server
        // FIXME: Add live otp server to e2e tests.
        assumeTrue(!ConfigUtils.isRunningCi && CommonTestUtils.IS_END_TO_END);
        MonitoredTrip monitoredTrip = new MonitoredTrip(OtpTestUtils.sendSamplePlanRequest());
        monitoredTrip.updateAllDaysOfWeek(true);
        monitoredTrip.userId = user.id;
        monitoredTrip.tripName = "My Morning Commute";
        monitoredTrip.itineraryExistence = new ItineraryExistence();
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);

        // Setup an OTP mock response in order to trigger some of the monitor checks.
        OtpResponse mockResponse = otpDispatcherResponse.getResponse();
        Itinerary mockMondayJune15Itinerary = mockResponse.plan.itineraries.get(0);

        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15
        updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
        );

        // Add fake alerts to simulated itinerary.
        ArrayList<LocalizedAlert> fakeAlerts = new ArrayList<>();
        fakeAlerts.add(new LocalizedAlert());
        mockMondayJune15Itinerary.legs.get(1).alerts = fakeAlerts;

        OtpTestUtils.setupOtpMocks(List.of(mockResponse));

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip);
        checkMonitoredTrip.run();
        // Assert that there is one notification generated during check.
        // TODO: Improve assertions to use snapshots.
        Assertions.assertEquals(1, checkMonitoredTrip.notifications.size());
        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
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

    private static List<DelayNotificationTestCase> createDelayNotificationTestCases ()
        throws URISyntaxException, CloneNotSupportedException {
        List<DelayNotificationTestCase> testCases = new ArrayList<>();

        // should not create departure/arrival notification for on-time trip
        CheckMonitoredTrip onTimeTrip = createCheckMonitoredTrip();
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
        JourneyState twentyMinutesLateJourneyStateWithUpdatedThreshold = createDefaultJourneyState();
        long eighteenMinutesInMilliseconds = TimeUnit.MILLISECONDS.convert(15, TimeUnit.MINUTES);
        twentyMinutesLateJourneyStateWithUpdatedThreshold.baselineDepartureTimeEpochMillis +=
            eighteenMinutesInMilliseconds;
        twentyMinutesLateJourneyStateWithUpdatedThreshold.baselineArrivalTimeEpochMillis +=
            eighteenMinutesInMilliseconds;
        CheckMonitoredTrip twentyMinutesLateTripWithUpdatedThreshold = createCheckMonitoredTrip(
            twentyMinutesLateJourneyStateWithUpdatedThreshold
        );
        offsetItineraryTime(
            twentyMinutesLateTripWithUpdatedThreshold.matchingItinerary,
            TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES)
        );
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
        JourneyState onTimeJourneyStateWithUpdatedThreshold = createDefaultJourneyState();
        onTimeJourneyStateWithUpdatedThreshold.baselineDepartureTimeEpochMillis += eighteenMinutesInMilliseconds;
        onTimeJourneyStateWithUpdatedThreshold.baselineArrivalTimeEpochMillis += eighteenMinutesInMilliseconds;
        CheckMonitoredTrip onTimeTripWithUpdatedThreshold = createCheckMonitoredTrip(
            onTimeJourneyStateWithUpdatedThreshold
        );
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
     * Convenience method for creating a CheckMonitoredTrip instance with the default journey state.
     */
    private static CheckMonitoredTrip createCheckMonitoredTrip() throws URISyntaxException, CloneNotSupportedException {
        return createCheckMonitoredTrip(createDefaultJourneyState());
    }

    /**
     * Creates a new CheckMonitoredTrip instance with a new non-persisted MonitoredTrip instance. The monitored trip is
     * created using the default OTP response. Also, creates a new matching itinerary that consists of the first
     * itinerary in the default OTP response.
     */
    private static CheckMonitoredTrip createCheckMonitoredTrip(
        JourneyState journeyState
    ) throws URISyntaxException, CloneNotSupportedException {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            otpDispatcherResponse.clone(),
            false,
            journeyState
        );
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
        assertEquals(
            testCase.shouldSkipTrip,
            testCase.generateCheckMonitoredTrip().shouldSkipMonitoredTripCheck(),
            testCase.message
        );
    }

    private static List<ShouldSkipTripTestCase> createSkipTripTestCases() throws URISyntaxException {
        List<ShouldSkipTripTestCase> testCases = new ArrayList<>();

        // - Return true for weekend trip when current time is on a weekday.
        MonitoredTrip weekendTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            otpDispatcherResponse,
            true,
            createDefaultJourneyState()
        );
        weekendTrip.updateAllDaysOfWeek(false);
        weekendTrip.saturday = true;
        weekendTrip.sunday = true;

        ShouldSkipTripTestCase weekendTripOnWeekdayTestCase = new ShouldSkipTripTestCase(
            "should return true for a weekend trip when current time is on a weekday",
            noonMonday8June2020, // mock time: June 8, 2020 (Wednesday)
            true
        );
        weekendTripOnWeekdayTestCase.trip = weekendTrip;
        testCases.add(weekendTripOnWeekdayTestCase);

        // - Return true for weekday trip when current time is on a weekend.
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for weekday trip when current time is on a weekend",
            noonMonday8June2020.withDayOfMonth(6), // mock time: June 6, 2020 (Saturday)
            true
        ));

        // - Return true if trip is starting today, but before lead time
        ShouldSkipTripTestCase weekdayTripBeforeLeadTimeTestCase = new ShouldSkipTripTestCase(
            "should return true if trip is starting today, but current time is before lead time",
            noonMonday8June2020.withHour(3).withMinute(0), // mock time: 3am,
            true
        );
        weekdayTripBeforeLeadTimeTestCase.lastCheckedTime = noonMonday8June2020.withHour(2).withMinute(0);
        testCases.add(weekdayTripBeforeLeadTimeTestCase);

        // - Return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago
        ShouldSkipTripTestCase weekdayTripChecked2HoursAgoTestCase = new ShouldSkipTripTestCase(
            "should return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago",
            noonMonday8June2020.withHour(6).withMinute(0), // mock time: 6am
            false
        );
        weekdayTripChecked2HoursAgoTestCase.lastCheckedTime = noonMonday8June2020.withHour(4).withMinute(0);
        testCases.add(weekdayTripChecked2HoursAgoTestCase);

        // - Return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago
        ShouldSkipTripTestCase weekdayTripIn1HourChecked2MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago",
            noonMonday8June2020.withHour(3).withMinute(0), // mock time: 3am
            true
        );
        weekdayTripIn1HourChecked2MinutesAgoTestCase.lastCheckedTime = noonMonday8June2020.withHour(2).withMinute(58);
        testCases.add(weekdayTripIn1HourChecked2MinutesAgoTestCase);

        // - Return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        ShouldSkipTripTestCase weekdayTripIn45MinutesChecked20MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago",
            noonMonday8June2020.withHour(7).withMinute(55), // mock time: 7:55am
            false
        );
        weekdayTripIn45MinutesChecked20MinutesAgoTestCase.lastCheckedTime = noonMonday8June2020.withHour(7).withMinute(35);
        testCases.add(weekdayTripIn45MinutesChecked20MinutesAgoTestCase);

        // - Return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago
        ShouldSkipTripTestCase weekdayTripIn45MinutesChecked2MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago",
            noonMonday8June2020.withHour(7).withMinute(55), // mock time: 7:55am
            true
        );
        weekdayTripIn45MinutesChecked2MinutesAgoTestCase.lastCheckedTime = noonMonday8June2020.withHour(7).withMinute(53);
        testCases.add(weekdayTripIn45MinutesChecked2MinutesAgoTestCase);

        // - Return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago
        ShouldSkipTripTestCase weekdayTripIn10MinutesChecked2MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago",
            noonMonday8June2020.withHour(8).withMinute(30), // mock time: 8:30am
            false
        );
        weekdayTripIn10MinutesChecked2MinutesAgoTestCase.lastCheckedTime = noonMonday8June2020.withHour(8).withMinute(28);
        testCases.add(weekdayTripIn10MinutesChecked2MinutesAgoTestCase);

        // - Returns false if trip still hadn't ended prior to the last checked time even though the current time is
        //   after the last known end time of the trip
        ShouldSkipTripTestCase weekdayTripNotYetEndedTestCase = new ShouldSkipTripTestCase(
            "should return false if trip hadn't ended the last time it was checked despite it being after the last known end time",
            noonMonday8June2020.withHour(8).withMinute(59), // mock time: 8:59am
            false
        );
        weekdayTripNotYetEndedTestCase.lastCheckedTime = noonMonday8June2020.withHour(8).withMinute(58).withSecond(0);
        testCases.add(weekdayTripNotYetEndedTestCase);

        // - Return true if trip has ended as of the last check 3 minutes ago
        ShouldSkipTripTestCase weekdayTripEndedTestCase = new ShouldSkipTripTestCase(
            "should return true if trip has ended as of the last check",
            noonMonday8June2020.withHour(9).withMinute(00), // mock time: 9:00am
            true
        );
        weekdayTripEndedTestCase.lastCheckedTime = noonMonday8June2020.withHour(8).withMinute(59);
        testCases.add(weekdayTripEndedTestCase);

        return testCases;
    }

    /**
     * Tests whether an OTP request can be made and if the trip and matching itinerary gets updated properly
     */
    @Test
    public void canMakeOTPRequestAndUpdateMatchingItineraryForPreviouslyUnmatchedItinerary()
        throws URISyntaxException, CloneNotSupportedException {
        // create a mock monitored trip and CheckMonitorTrip instance
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip();
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not possible on Mondays as of the last check
        mockTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetDate = "2020-06-15";
        mockCheckMonitoredTrip.targetZonedDateTime = noonMonday8June2020
            .withDayOfMonth(15)
            .withHour(8)
            .withMinute(35);

        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = otpDispatcherResponse.getResponse();
        Itinerary mockMondayJune15Itinerary = mockWeekdayResponse.plan.itineraries.get(0);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15
        updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
        );
        OtpTestUtils.setupOtpMocks(List.of(mockWeekdayResponse));

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertEquals(true, mockCheckMonitoredTrip.makeOTPRequestAndUpdateMatchingItinerary());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that status is active
        assertEquals(
            TripStatus.TRIP_ACTIVE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should be active"
        );

        // verify itinerary existence was updated to show trip is possible again
        assertEquals(
            true,
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated trip should be valid on Monday"
        );

        // verify that trip is still possible overall
        assertEquals(
            false,
            updatedTrip.journeyState.noLongerPossible,
            "updated Trip should indicate it is still possible on at least one day of the week"
        );
    }

    /**
     * Tests whether an OTP request can be made and if the trip is properly updated after not being able to find a
     * matching itinerary.
     */
    @Test
    public void canMakeOTPRequestAndResolveUnmatchedItinerary() throws URISyntaxException, CloneNotSupportedException {
        // create a mock monitored trip and CheckMonitorTrip instance
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip();
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip that indicates the trip was still possible on Mondays as of the last
        // check
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetDate = "2020-06-15";
        mockCheckMonitoredTrip.targetZonedDateTime = noonMonday8June2020
            .withDayOfMonth(15)
            .withHour(8)
            .withMinute(35);

        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = otpDispatcherResponse.getResponse();
        Itinerary mockMondayJune15Itinerary = mockWeekdayResponse.plan.itineraries.get(0);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15, but at a time
        // that does not match the previous itinerary
        updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
                .withMinute(22) // this will cause an itinerary mismatch
        );
        OtpTestUtils.setupOtpMocks(List.of(mockWeekdayResponse));

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertEquals(false, mockCheckMonitoredTrip.makeOTPRequestAndUpdateMatchingItinerary());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that status is active
        assertEquals(
            TripStatus.NEXT_TRIP_NOT_POSSIBLE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should indicate trip is not possible this day"
        );

        // verify itinerary existence was updated to show trip is not possible today
        assertEquals(
            false,
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated Trip should not be valid on Monday"
        );

        // verify that trip is still possible overall
        assertEquals(
            false,
            updatedTrip.journeyState.noLongerPossible,
            "updated Trip should indicate it is still possible on at least one day of the week"
        );
    }

    /**
     * Tests whether an OTP request can be made and if the trip is properly updated after not being able to find a
     * matching itinerary for all days of the week.
     */
    @Test
    public void canMakeOTPRequestAndResolveNoLongerPossibleTrip() throws URISyntaxException,
        CloneNotSupportedException {
        // create a mock monitored trip and CheckMonitorTrip instance
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip();
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip for Mondays
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not possible on Mondays as of the last check
        mockTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetDate = "2020-06-15";
        mockCheckMonitoredTrip.targetZonedDateTime = noonMonday8June2020
            .withDayOfMonth(15)
            .withHour(8)
            .withMinute(35);

        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = otpDispatcherResponse.getResponse();
        Itinerary mockMondayJune15Itinerary = mockWeekdayResponse.plan.itineraries.get(0);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15, but at a time
        // that does not match the previous itinerary
        updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
                .withMinute(22) // this will cause an itinerary mismatch
        );
        OtpTestUtils.setupOtpMocks(List.of(mockWeekdayResponse));

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertEquals(false, mockCheckMonitoredTrip.makeOTPRequestAndUpdateMatchingItinerary());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that status is active
        assertEquals(
            TripStatus.NO_LONGER_POSSIBLE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should indicate trip is no longer possible"
        );

        // verify itinerary existence was updated to show trip is not possible today
        assertEquals(
            false,
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated Trip should not be valid on Monday"
        );

        // verify that trip is no longer possible overall
        assertEquals(
            true,
            updatedTrip.journeyState.noLongerPossible,
            "updated Trip should indicate it is no longer possible"
        );
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
        /**
         * The last time a journey was checked. If this is not set, it is assumed that the trip has never been checked
         * before.
         */
        public ZonedDateTime lastCheckedTime;

        /* a helpful message describing the particular test case */
        public final String message;

        /* The time to mock */
        public final ZonedDateTime mockTime;

        /**
         * if true, it is expected that the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method should
         * calculate that the given trip should be skipped.
         */
        public final boolean shouldSkipTrip;

        /**
         * The trip for the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method to calculate whether
         * skipping trip analysis should occur. If this is not set, then a default weekday trip will be created and
         * used.
         */
        public MonitoredTrip trip;

        private ShouldSkipTripTestCase(String message, ZonedDateTime mockTime, boolean shouldSkipTrip) {
            this.message = message;
            this.mockTime = mockTime;
            this.shouldSkipTrip = shouldSkipTrip;
        }

        @Override
        public String toString() {
            return message;
        }

        public CheckMonitoredTrip generateCheckMonitoredTrip() throws URISyntaxException, CloneNotSupportedException {
            // create a mock OTP response for planning a trip on a weekday target datetime
            OtpResponse mockWeekdayResponse = otpDispatcherResponse.getResponse();
            Itinerary mockWeekdayItinerary = mockWeekdayResponse.plan.itineraries.get(0);
            updateBaseItineraryTime(
                mockWeekdayItinerary,
                mockTime.withYear(2020).withMonth(6).withDayOfMonth(8).withHour(8).withMinute(40).withSecond(10)
            );

            // create these entries in the database at this point to ensure the correct mocked time is set
            // if trip is null, create the default weekday trip
            if (trip == null) {
                trip = PersistenceTestUtils
                    .createMonitoredTrip(user.id, otpDispatcherResponse, true, createDefaultJourneyState());
            }

            // if last checked time is not null, there is an assumption that the journey state has been created before.
            // Therefore, create a mock journey state and set the matching itinerary to be the mock weekday itinerary.
            // Also, set the journeyState's last checked time to the provided lastCheckedTime.
            if (lastCheckedTime != null) {
                JourneyState journeyState = trip.journeyState;
                journeyState.matchingItinerary = mockWeekdayItinerary;
                journeyState.targetDate = "2020-06-08";
                journeyState.lastCheckedEpochMillis = lastCheckedTime.toInstant().toEpochMilli();
                Persistence.monitoredTrips.replace(trip.id, trip);
            }
            return new CheckMonitoredTrip(trip);
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
            baseZonedDateTime.toInstant().toEpochMilli() - mockItinerary.startTime.getTime()
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
