package org.opentripplanner.middleware.tripmonitor.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import jersey.repackaged.com.google.common.collect.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MobilityProfileLite;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.TripStatus;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.models.TripMonitorNotification.STOPWATCH_ICON;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.firstItinerary;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.NEXT_TRIP_NOT_POSSIBLE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.NO_LONGER_POSSIBLE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.PAST_TRIP;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.TRIP_ACTIVE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.TRIP_UPCOMING;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTripBasicTest.makeMonitoredTripFromNow;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTripBasicTest.setRecurringTodayAndTomorrow;

/**
 * This class contains tests for the {@link CheckMonitoredTrip} job.
 */
public class CheckMonitoredTripTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTripTest.class);
    public static final int ONE_DAY_IN_MILLIS = 24 * 3600000;
    private static OtpUser user;

    // this is initialized in the setup method after the OTP_TIMEZONE config value is known.
    private static final ZonedDateTime MONDAY_20200608_NOON = DateTimeUtils.makeOtpZonedDateTime(new Date())
        .withYear(2020)
        .withMonth(6)
        .withDayOfMonth(8)
        .withHour(12)
        .withMinute(0);
    private static final ZonedDateTime MONDAY_20200615_0845 = MONDAY_20200608_NOON
        .withDayOfMonth(15)
        .withHour(8)
        .withMinute(45);
    private static final ZonedDateTime MONDAY_20200615_0835 = MONDAY_20200615_0845.withMinute(35);

    @BeforeAll
    public static void setup() {
        user = PersistenceTestUtils.createUser("user@example.com");
    }

    @AfterAll
    public static void tearDown() {
        user.delete(false);
    }

    @AfterEach
    public void tearDownAfterTest() {
        DateTimeUtils.useSystemDefaultClockAndTimezone();
    }

    /** Provides a mock OTP 'plan' response */
    public OtpResponse mockOtpPlanResponse() {
        try {
            // Setup an OTP mock response in order to trigger some of the monitor checks.
            return OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.getResponse();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void canMonitorOngoingTrip() throws Exception {
        // Setup an OTP mock response in order to trigger some of the monitor checks.
        OtpResponse mockResponse = getMockOtpResponseJune15();
        Itinerary itinerary = firstItinerary(mockResponse);

        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            null
        );
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        // For an ongoing trip, assume the journey state has been initialized.
        monitoredTrip.journeyState.baselineDepartureTimeEpochMillis = itinerary.startTime.getTime();
        monitoredTrip.journeyState.baselineArrivalTimeEpochMillis = itinerary.endTime.getTime();
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);

        // Add fake alerts to simulated itinerary.
        ArrayList<LocalizedAlert> fakeAlerts = new ArrayList<>();
        fakeAlerts.add(new LocalizedAlert());
        itinerary.legs.get(1).alerts = fakeAlerts;

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, () -> mockResponse);
        checkMonitoredTrip.run();

        // Assert that there is one notification generated during check and it is an alert.
        assertEquals(1, checkMonitoredTrip.notifications.size());
        assertEquals(NotificationType.ALERT_FOUND, checkMonitoredTrip.notifications.iterator().next().type);
        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    @Test
    void canMonitorFutureTrip() throws Exception {
        // TODO refactor with above test.
        // Save an itinerary in the future, and run an itinerary check on it at at time before that itinerary start.

        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            null
        );
        monitoredTrip.tripTime = "08:35";
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();

        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);


        OtpResponse mockResponse = mockOtpPlanResponse();
        Itinerary mockTuesdayJune9Itinerary = firstItinerary(mockResponse);
        OtpTestUtils.setItineraryDay(mockTuesdayJune9Itinerary, 9);

        // Add fake alerts to simulated itinerary.
        mockTuesdayJune9Itinerary.legs.get(1).alerts = Lists.newArrayList(new LocalizedAlert());

        // The trip is set to be monitored Monday to Friday.
        // Mock time to be 7:30am on Tuesday, June 9 before the trip start.
        DateTimeUtils.useFixedClockAt(
            MONDAY_20200608_NOON
                .withDayOfMonth(9)
                .withHour(7)
                .withMinute(30)
        );

        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, () -> mockResponse);
        checkMonitoredTrip.run();
        // This process should initialize the scheduled departure time on the journey state
        assertNotEquals(0, checkMonitoredTrip.trip.journeyState.scheduledDepartureTimeEpochMillis);

        // No notifications in this case because the trip is next day.
        assertEquals(1, checkMonitoredTrip.notifications.size());
        assertEquals(NotificationType.ALERT_FOUND, checkMonitoredTrip.notifications.iterator().next().type);

        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    @Test
    void sendInitialReminderNotificationForOneTimeTrip() throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            null
        );

        // Set one time trip state.
        monitoredTrip.updateAllDaysOfWeek(false);
        monitoredTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;
        Persistence.monitoredTrips.create(monitoredTrip);

        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, this::getMockOtpResponseJune15);
        checkMonitoredTrip.run();
        // Assert that the initial reminder has been generated.
        Assertions.assertNotNull(checkMonitoredTrip.initialReminderNotification);
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    @ParameterizedTest
    @MethodSource("createDelayNotificationTestCases")
    void testDelayNotifications(
        int minutesLate,
        int previousMinutesLate,
        NotificationType notificationType,
        String expectedNotificationPattern,
        String message
    ) throws Exception {
        long previousDelayMillis = TimeUnit.MILLISECONDS.convert(previousMinutesLate, TimeUnit.MINUTES);
        JourneyState journeyState = OtpTestUtils.createDefaultJourneyState();

        if (notificationType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY || notificationType == NotificationType.DEPARTURE_DELAY) {
            journeyState.baselineDepartureTimeEpochMillis += previousDelayMillis;
        }
        if (notificationType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY || notificationType == NotificationType.ARRIVAL_DELAY) {
            journeyState.baselineArrivalTimeEpochMillis += previousDelayMillis;
        }

        CheckMonitoredTrip check = createCheckMonitoredTrip(journeyState, this::mockOtpPlanResponse);

        if (notificationType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY || notificationType == NotificationType.DEPARTURE_DELAY) {
            check.matchingItinerary.offsetStart(TimeUnit.MILLISECONDS.convert(minutesLate, TimeUnit.MINUTES));
        }
        if (notificationType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY || notificationType == NotificationType.ARRIVAL_DELAY) {
            check.matchingItinerary.offsetEnd(TimeUnit.MILLISECONDS.convert(minutesLate, TimeUnit.MINUTES));
        }

        TripMonitorNotification notification = check.checkTripForDelays();
        if (expectedNotificationPattern == null) {
            assertNull(notification, message);
        } else {
            assertNotNull(notification);
            assertEquals(notificationType, notification.type);
            assertThat(message, notification.body, matchesPattern(expectedNotificationPattern));
        }
    }

    private static Stream<Arguments> createDelayNotificationTestCases() {
        // These cases assume the default delay threshold of 15 minutes.

        // Note on patterns in the cases below:
        // JDK 20 uses narrow no-break space U+202F before "PM" for time format; earlier JDKs just use a space.
        return Stream.of(
            Arguments.of(
                0,
                0,
                NotificationType.DEPARTURE_AND_ARRIVAL_DELAY,
                null,
                "On-time trip previously on-time => no delay notification"
            ),
            Arguments.of(
                20,
                0,
                NotificationType.DEPARTURE_AND_ARRIVAL_DELAY,
                STOPWATCH_ICON +
                " Your trip is now predicted to depart 20 minutes late at 9:00[\\u202f ]AM \\(Now arriving at 9:18[\\u202f ]AM\\)\\.",
                "20m-late trip previously on-time => show dep/arr delay notifications"
            ),
            Arguments.of(
                20,
                0,
                NotificationType.DEPARTURE_DELAY,
                STOPWATCH_ICON +
                " Your trip is now predicted to depart 20 minutes late \\(at 9:00[\\u202f ]AM\\)\\.",
                "20m-late departure previously on-time, but still arriving on-time => show departure-only delay notifications"
            ),
            Arguments.of(
                20,
                0,
                NotificationType.ARRIVAL_DELAY,
                STOPWATCH_ICON +
                " Your trip is now predicted to arrive 20 minutes late \\(at 9:18[\\u202f ]AM\\)\\.",
                "20m-late arrival previously on-time, but still departing on-time => show arrival-only delay notifications"
            ),
            Arguments.of(
                -18,
                0,
                NotificationType.DEPARTURE_AND_ARRIVAL_DELAY,
                STOPWATCH_ICON +
                " Your trip is now predicted to depart 18 minutes early at 8:22[\\u202f ]AM \\(Now arriving at 8:40[\\u202f ]AM\\)\\.",
                "18m-early trip previously on-time => show delay (early) notifications"
            ),
            Arguments.of(
                20,
                15,
                NotificationType.DEPARTURE_AND_ARRIVAL_DELAY,
                null,
                "Trip previously 15m late, now 20m late => no notification"
            ),
            Arguments.of(
                0,
                15,
                NotificationType.DEPARTURE_AND_ARRIVAL_DELAY,
                STOPWATCH_ICON +
                " Your trip is now predicted to depart about on time at 8:40[\\u202f ]AM \\(Now arriving at 8:58[\\u202f ]AM\\)\\.",
                "On-time trip previously late => show on-time notifications"
            )
        );
    }

    /**
     * Convenience method for creating a CheckMonitoredTrip instance with the default journey state.
     */
    private static CheckMonitoredTrip createCheckMonitoredTrip(Supplier<OtpResponse> otpResponseProvider) throws Exception {
        return createCheckMonitoredTrip(OtpTestUtils.createDefaultJourneyState(otpResponseProvider), otpResponseProvider);
    }

    /**
     * Creates a new CheckMonitoredTrip instance with a new non-persisted MonitoredTrip instance. The monitored trip is
     * created using the default OTP response. Also, creates a new matching itinerary that consists of the first
     * itinerary in the default OTP response.
     */
    private static CheckMonitoredTrip createCheckMonitoredTrip(JourneyState journeyState, Supplier<OtpResponse> otpResponseProvider) throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            journeyState
        );
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, otpResponseProvider);
        checkMonitoredTrip.matchingItinerary = OtpTestUtils.createDefaultItinerary();
        return checkMonitoredTrip;
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
            testCase.generateCheckMonitoredTrip(user).shouldSkipMonitoredTripCheck(),
            testCase.message
        );
    }

    static List<ShouldSkipTripTestCase> createSkipTripTestCases() throws Exception {
        List<ShouldSkipTripTestCase> testCases = new ArrayList<>();

        MonitoredTrip weekendTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE,
            true,
            OtpTestUtils.createDefaultJourneyState()
        );
        weekendTrip.updateAllDaysOfWeek(false);
        weekendTrip.saturday = true;
        weekendTrip.sunday = true;

        ShouldSkipTripTestCase weekendTripOnWeekdayTestCase = new ShouldSkipTripTestCase(
            "should return true for a weekend trip when current time is on a weekday",
            MONDAY_20200608_NOON,
            true
        );
        weekendTripOnWeekdayTestCase.trip = weekendTrip;
        testCases.add(weekendTripOnWeekdayTestCase);

        // - Return true for weekday trip when current time is on a weekend.
        testCases.add(new ShouldSkipTripTestCase(
            "should return true for weekday trip when current time is on a weekend",
            MONDAY_20200608_NOON.withDayOfMonth(6), // mock time: June 6, 2020 (Saturday)
            true
        ));

        // - Return true if trip is starting today, but before lead time
        ShouldSkipTripTestCase weekdayTripBeforeLeadTimeTestCase = new ShouldSkipTripTestCase(
            "should return true if trip is starting today, but current time is before lead time",
            MONDAY_20200608_NOON.withHour(3).withMinute(0), // mock time: 3am,
            true
        );
        weekdayTripBeforeLeadTimeTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(2).withMinute(0);
        testCases.add(weekdayTripBeforeLeadTimeTestCase);

        // - Return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago
        ShouldSkipTripTestCase weekdayTripChecked2HoursAgoTestCase = new ShouldSkipTripTestCase(
            "should return false if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago",
            MONDAY_20200608_NOON.withHour(6).withMinute(0), // mock time: 6am
            false
        );
        weekdayTripChecked2HoursAgoTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(4).withMinute(0);
        testCases.add(weekdayTripChecked2HoursAgoTestCase);

        // - Return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago
        ShouldSkipTripTestCase weekdayTripIn1HourChecked2MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return true if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago",
            MONDAY_20200608_NOON.withHour(3).withMinute(0), // mock time: 3am
            true
        );
        weekdayTripIn1HourChecked2MinutesAgoTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(2).withMinute(58);
        testCases.add(weekdayTripIn1HourChecked2MinutesAgoTestCase);

        // - Return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        ShouldSkipTripTestCase weekdayTripIn45MinutesChecked20MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return false if trip is starting in 45 minutes and the last time checked was 20 minutes ago",
            MONDAY_20200608_NOON.withHour(7).withMinute(55), // mock time: 7:55am
            false
        );
        weekdayTripIn45MinutesChecked20MinutesAgoTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(7).withMinute(35);
        testCases.add(weekdayTripIn45MinutesChecked20MinutesAgoTestCase);

        // - Return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago
        ShouldSkipTripTestCase weekdayTripIn45MinutesChecked2MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return true if trip is starting in 45 minutes and the last time checked was 2 minutes ago",
            MONDAY_20200608_NOON.withHour(7).withMinute(55), // mock time: 7:55am
            true
        );
        weekdayTripIn45MinutesChecked2MinutesAgoTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(7).withMinute(53);
        testCases.add(weekdayTripIn45MinutesChecked2MinutesAgoTestCase);

        // - Return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago
        ShouldSkipTripTestCase weekdayTripIn10MinutesChecked2MinutesAgoTestCase = new ShouldSkipTripTestCase(
            "should return false if trip is starting in 10 minutes and the last time checked was 2 minutes ago",
            MONDAY_20200608_NOON.withHour(8).withMinute(30), // mock time: 8:30am
            false
        );
        weekdayTripIn10MinutesChecked2MinutesAgoTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(8).withMinute(28);
        testCases.add(weekdayTripIn10MinutesChecked2MinutesAgoTestCase);

        // - Returns false if trip still hadn't ended prior to the last checked time even though the current time is
        //   after the last known end time of the trip
        ShouldSkipTripTestCase weekdayTripNotYetEndedTestCase = new ShouldSkipTripTestCase(
            "should return false if trip hadn't ended the last time it was checked despite it being after the last known end time",
            MONDAY_20200608_NOON.withHour(8).withMinute(59), // mock time: 8:59am
            false
        );
        weekdayTripNotYetEndedTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(8).withMinute(58).withSecond(0);
        testCases.add(weekdayTripNotYetEndedTestCase);

        // - Return true if trip has ended as of the last check 3 minutes ago
        ShouldSkipTripTestCase weekdayTripEndedTestCase = new ShouldSkipTripTestCase(
            "should return true if trip has ended as of the last check",
            MONDAY_20200608_NOON.withHour(9).withMinute(0), // mock time: 9:00am
            true
        );
        weekdayTripEndedTestCase.lastCheckedTime = MONDAY_20200608_NOON.withHour(8).withMinute(59);
        testCases.add(weekdayTripEndedTestCase);

        return testCases;
    }

    /**
     * Tests whether an OTP request can be made and if the trip and matching itinerary gets updated properly
     */
    @Test
    void canMakeOTPRequestAndUpdateMatchingItineraryForPreviouslyUnmatchedItinerary() throws Exception {
        // create a mock monitored trip and CheckMonitorTrip instance.
        // Note that the response below gets modified from the original mockOtpPlanResponse.
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip(this::getMockOtpResponseJune15);
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not possible on Mondays as of the last check
        mockTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetZonedDateTime = MONDAY_20200615_0835;

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertTrue(mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that status is active
        assertEquals(
            TRIP_ACTIVE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should be active"
        );

        // verify itinerary existence was updated to show trip is possible again
        assertTrue(
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated trip should be valid on Monday"
        );
    }

    /**
     * Tests whether an OTP request can be made and if the trip is properly updated after not being able to find a
     * matching itinerary.
     */
    @Test
    void canMakeOTPRequestAndResolveUnmatchedItinerary() throws Exception {
        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        // create a mock monitored trip and CheckMonitorTrip instance
        // Note that the response below gets modified from the original mockOtpPlanResponse.
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip(() -> mockWeekdayResponse);
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip that indicates the trip was still possible on Mondays as of the last
        // check
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetZonedDateTime = MONDAY_20200615_0835;

        Itinerary mockMondayJune15Itinerary = firstItinerary(mockWeekdayResponse);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15, but at a time
        // that does not match the previous itinerary
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
                .withMinute(22) // this will cause an itinerary mismatch
        );

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertFalse(mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that status is active
        assertEquals(
            NEXT_TRIP_NOT_POSSIBLE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should indicate trip is not possible this day"
        );

        // verify itinerary existence was updated to show trip is not possible today
        assertFalse(
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated Trip should not be valid on Monday"
        );

        // verify a notification was sent indicating that the next trip is not possible
        assertEquals(
            1,
            mockCheckMonitoredTrip.notifications.size(),
            "A notification should be generated for the next trip not being possible"
        );
        assertEquals(
            "The trip planner was unable to find your trip today after a few attempts and has snoozed monitoring as a result. Go to Trip Details to resume monitoring or plan a new trip.",
            mockCheckMonitoredTrip.notifications.iterator().next().body,
            "The notification should have the appropriate message when the next trip is not possible"
        );
    }

    /**
     * Tests whether an OTP request can be made and if the trip is properly updated after not being able to find a
     * matching itinerary for all days of the week.
     */
    @Test
    void canMakeOTPRequestAndResolveNoLongerPossibleTrip() throws Exception {
        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        // create a mock monitored trip and CheckMonitorTrip instance
        // Note that the response below gets modified from the original mockOtpPlanResponse.
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip(() -> mockWeekdayResponse);
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip for Mondays
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not possible on Mondays as of the last check
        mockTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetZonedDateTime = MONDAY_20200615_0835;

        Itinerary mockMondayJune15Itinerary = firstItinerary(mockWeekdayResponse);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15, but at a time
        // that does not match the previous itinerary
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
                .withMinute(22) // this will cause an itinerary mismatch
        );

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertFalse(mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that trip status is no longer possible
        assertEquals(
            NO_LONGER_POSSIBLE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should indicate trip is no longer possible"
        );

        // verify itinerary existence was updated to show trip is not possible today
        assertFalse(
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated Trip should not be valid on Monday"
        );

        // verify a notification was sent indicating that the trip is no longer possible
        assertEquals(
            1,
            mockCheckMonitoredTrip.notifications.size(),
            "A notification should be generated for the next trip not being possible"
        );
        assertEquals(
            "Your itinerary is no longer possible on any monitored day of the week. Please plan and save a new trip.",
            mockCheckMonitoredTrip.notifications.iterator().next().body,
            "The notification should have the appropriate message when the trip is no longer possible"
        );
    }

    /**
     * Tests whether the journey state is updated after monitored days are changed.
     */
    @ParameterizedTest
    @MethodSource("casesForChangingMonitoredDays")
    void canHandleChangingMonitoredDays(
        ZonedDateTime nowTime,
        int previousTargetDay,
        String currentTargetDate,
        String expectedTargetDate,
        TripStatus tripStatus,
        boolean skipMondayTuesday
    ) throws Exception {
        DateTimeUtils.useFixedClockAt(nowTime);

        // Create an OTP mock to return, with itinerary start on Monday, June 8, 2020.
        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        Itinerary originalItinerary = firstItinerary(mockWeekdayResponse);
        OtpTestUtils.setItineraryDay(originalItinerary, 8);

        Date originalStartTime = originalItinerary.startTime;

        // Create an OTP mock to return, with itinerary start on Tuesday, June 9, 2020.
        OtpResponse mockPreviousWeekdayResponse = mockOtpPlanResponse();
        Itinerary mockPreviousItinerary = firstItinerary(mockPreviousWeekdayResponse);
        OtpTestUtils.setItineraryDay(mockPreviousItinerary, 9);
        if (tripStatus == TRIP_ACTIVE) {
            // If the trip is active, set the trip day to Monday when that case applies.
            mockPreviousItinerary.offsetTimes(-ONE_DAY_IN_MILLIS);
        }

        // Make sure that the start time on the original trip was not changed inadvertently.
        assertEquals(originalStartTime, originalItinerary.startTime);

        // Create a mock monitored trip and CheckMonitorTrip instance.
        // Note that the response below includes changes above to the itinerary times.
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip(() -> mockWeekdayResponse);
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;

        if (skipMondayTuesday) {
            // All days are initially monitored.
            // For some cases, un-monitor Monday and Tuesday, so that the next trip date is Wednesday.
            mockTrip.monday = false;
            mockTrip.tuesday = false;
        }

        // The trip exists Monday, Tuesday, and Wednesday.
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        mockTrip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();
        mockTrip.itineraryExistence.wednesday = new ItineraryExistence.ItineraryExistenceResult();

        // Use a previously computed trip status with the specified currentTargetDate.
        // Copy those journey state params, including to the CheckMonitoredTrip object too.
        mockTrip.journeyState.tripStatus = tripStatus;
        mockTrip.journeyState.targetDate = currentTargetDate;
        mockTrip.journeyState.matchingItinerary = mockPreviousItinerary;
        mockCheckMonitoredTrip.previousJourneyState = new JourneyState();
        mockCheckMonitoredTrip.previousJourneyState.targetDate = mockTrip.journeyState.targetDate;
        mockCheckMonitoredTrip.previousJourneyState.tripStatus = mockTrip.journeyState.tripStatus;
        mockCheckMonitoredTrip.previousJourneyState.matchingItinerary = mockPreviousItinerary;
        mockCheckMonitoredTrip.previousMatchingItinerary = mockPreviousItinerary;

        // Set the current target date/time.
        mockCheckMonitoredTrip.targetZonedDateTime = MONDAY_20200615_0835.withDayOfMonth(previousTargetDay);

        Persistence.monitoredTrips.create(mockTrip);

        // Perform the skip check (this populates some internal states).
        // This trip should not be skipped if Monday and Tuesday are monitored because,
        // for those cases, the trip occurs within minutes of the mocked system time.
        assertEquals(skipMondayTuesday, mockCheckMonitoredTrip.shouldSkipMonitoredTripCheck());

        // Execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome.
        assertTrue(mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // Fetch updated trip from persistence and check the trip status and target date.
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        if (!skipMondayTuesday) {
            assertTrue(updatedTrip.monday);
        }

        assertEquals(
            tripStatus,
            updatedTrip.journeyState.tripStatus,
             tripStatus == TRIP_UPCOMING
                 ? "Trip state should remain in future."
                 : "Active trips will continue to be monitored until they end."
        );

        assertEquals(
            expectedTargetDate,
            updatedTrip.journeyState.targetDate,
            "Trip target date should have been changed according to monitored days."
        );
    }

    private static Stream<Arguments> casesForChangingMonitoredDays() {
        ZonedDateTime monday0830 = MONDAY_20200608_NOON
            .withHour(8)
            .withMinute(30);
        return Stream.of(
            Arguments.of(
                monday0830,
                8,
                "2020-06-08",
                "2020-06-10",
                TRIP_UPCOMING,
                true
            ),
            Arguments.of(
                // Mock the current time to same day of itinerary, between itinerary start and end time.
                monday0830.withMinute(50),
                8,
                "2020-06-08",
                "2020-06-08",
                TRIP_ACTIVE,
                true
            ),
            Arguments.of(
                monday0830,
                9,
                "2020-06-09",
                "2020-06-08",
                TRIP_UPCOMING,
                // This trip should not be skipped because it occurs within minutes of the mocked system time.
                false
            )
        );
    }

    /**
     * Tests whether the journey state is updated after monitored days are changed.
     * This test also involves a matching itinerary a day ahead of the days to monitor.
     */
    @Test
    void canUpdateTargetDate() throws Exception {
        // Create a check with a mock OTP response itinerary for Tuesday 09 June 2020 08:40.
        CheckMonitoredTrip check = createCheckMonitoredTrip(this::mockOtpPlanResponse);
        MonitoredTrip trip = check.trip;

        trip.wednesday = true;
        trip.tuesday = true;
        // Set the clock to Tuesday before trip start.
        ZonedDateTime tuesdayBeforeTripStarts = MONDAY_20200608_NOON.withDayOfMonth(9).withHour(7);
        DateTimeUtils.useFixedClockAt(tuesdayBeforeTripStarts);

        // The trip exists Monday, Tuesday, and Wednesday.
        trip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        trip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();
        trip.itineraryExistence.wednesday = new ItineraryExistence.ItineraryExistenceResult();

        // Set the matching itinerary and previous matching itinerary to Wednesday
        trip.journeyState.matchingItinerary = firstItinerary(mockOtpPlanResponse());
        trip.journeyState.matchingItinerary.offsetTimes(ONE_DAY_IN_MILLIS);
        check.previousMatchingItinerary.offsetTimes(ONE_DAY_IN_MILLIS);
        assertEquals(
            "2020-06-10",
            DateTimeUtils.getStringFromDate(
                DateTimeUtils.makeOtpZonedDateTime(trip.journeyState.matchingItinerary.startTime).toLocalDate(),
                DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN
            )
        );

        trip.journeyState.lastCheckedEpochMillis = DateTimeUtils.nowAsZonedDateTime().minusMinutes(30).toInstant().toEpochMilli();

        // Set target date to Wednesday
        trip.journeyState.targetDate = "2020-06-10";

        Persistence.monitoredTrips.create(trip);

        // Run the checks. Test the new target date
        check.run();

        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(trip.id);

        assertEquals(
            "2020-06-09",
            updatedTrip.journeyState.targetDate
        );
        assertEquals(
            "2020-06-09",
            DateTimeUtils.getStringFromDate(
                DateTimeUtils.makeOtpZonedDateTime(updatedTrip.journeyState.matchingItinerary.startTime).toLocalDate(),
                DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN
            )
        );
    }

    @Test
    void shouldReportOneTimeTripInPastAsCompleted() throws CloneNotSupportedException {
        MonitoredTrip trip = makeMonitoredTripFromNow(-900, -300);
        trip.journeyState.matchingItinerary = trip.itinerary;
        trip.journeyState.tripStatus = TRIP_ACTIVE;

        new CheckMonitoredTrip(trip, this::mockOtpPlanResponse).checkOtpAndUpdateTripStatus();
        assertEquals(PAST_TRIP, trip.journeyState.tripStatus);
    }

    @Test
    void shouldReportOneTimeTripInPastWithTrackingAsActive() throws CloneNotSupportedException {
        MonitoredTrip trip = createPastActiveTripWithTrackedJourney();

        new CheckMonitoredTrip(trip, this::mockOtpPlanResponse).checkOtpAndUpdateTripStatus();
        assertEquals(TRIP_ACTIVE, trip.journeyState.tripStatus);
    }

    private static MonitoredTrip createPastActiveTripWithTrackedJourney() {
        MonitoredTrip trip = createPastActiveTrip();

        TrackedJourney journey = new TrackedJourney();
        journey.id = UUID.randomUUID().toString();
        journey.tripId = trip.id;
        journey.startTime = trip.itinerary.startTime;
        // No end time or condition provided in this case as tracking is still ongoing.
        Persistence.trackedJourneys.create(journey);

        return trip;
    }

    private static MonitoredTrip createPastActiveTrip() {
        MonitoredTrip trip = makeMonitoredTripFromNow(-900, -300);
        String todayFormatted = getShiftedDay(trip.itinerary.startTime, 0);
        trip.id =  UUID.randomUUID().toString();
        trip.userId = user.id;
        trip.journeyState.matchingItinerary = trip.itinerary;
        trip.journeyState.targetDate = todayFormatted;
        trip.journeyState.tripStatus = TRIP_ACTIVE;
        Persistence.monitoredTrips.create(trip);
        return trip;
    }

    @Test
    void shouldReportRecurringTripInstanceInPastAsUpcoming() throws Exception {
        MonitoredTrip trip = createPastActiveTrip();
        setRecurringTodayAndTomorrow(trip);

        // Build fake OTP response, using an existing one as template
        OtpResponse otpResponse = mockOtpPlanResponse();
        Itinerary adjustedItinerary = trip.itinerary.clone();
        otpResponse.plan.itineraries = List.of(adjustedItinerary);

        // Note that the response below gets modified from the original mockOtpPlanResponse.
        CheckMonitoredTrip check = new CheckMonitoredTrip(trip, () -> otpResponse);
        // Trip is advanced to next monitored day because "today"'s trip instance has ended.
        // As a result, trip status is set to upcoming, checkOtpAndUpdateTripStatus is skipped.
        assertTrue(check.shouldSkipMonitoredTripCheck());
        assertEquals(TripStatus.TRIP_UPCOMING, check.journeyState.tripStatus);
        assertEquals(TripStatus.TRIP_UPCOMING, trip.journeyState.tripStatus);
        assertEquals(getShiftedDay(trip.itinerary.startTime, 1), trip.journeyState.targetDate);
    }

    @Test
    void shouldReportRecurringTripInstanceInPastWithTrackingAsActive() throws Exception {
        MonitoredTrip trip = createPastActiveTripWithTrackedJourney();
        setRecurringTodayAndTomorrow(trip);
        String todayFormatted = trip.journeyState.targetDate;

        CheckMonitoredTrip check = new CheckMonitoredTrip(trip, this::mockOtpPlanResponse);
        check.shouldSkipMonitoredTripCheck(false);
        check.checkOtpAndUpdateTripStatus();
        // Trip should remain active, and the target date should still be "today".
        assertEquals(TRIP_ACTIVE, trip.journeyState.tripStatus);
        assertEquals(todayFormatted, trip.journeyState.targetDate);
    }

    static String getShiftedDay(Date startTime, int dayShift) {
        Instant startInstant = startTime.toInstant();
        Date nextDayStart = Date.from(startInstant.plus(dayShift, ChronoUnit.DAYS));
        return DateTimeUtils.makeOtpZonedDateTime(nextDayStart).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Test
    void canBeTolerantWithItineraryChecks() throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            OtpTestUtils.createDefaultJourneyState()
        );
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        Persistence.monitoredTrips.create(monitoredTrip);

        OtpResponse expectedResponse = getMockOtpResponseJune15();
        OtpResponse unexpectedResponse = getMockOtpResponseJune15();
        // Remove the final itinerary leg so that the matching itineraries check fails.
        firstItinerary(unexpectedResponse).legs.remove(2);

        // Mock the current time to be 8:45am on Monday, June 15, 2020.
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // Match on first attempts.
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, true, 0, TRIP_ACTIVE);
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, false, 0, TRIP_ACTIVE);

        // Fail on first attempt.
        assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, false, 0, NEXT_TRIP_NOT_POSSIBLE);

        // Reactivate trip.
        monitoredTrip.journeyState.tripStatus = TRIP_ACTIVE;
        Persistence.monitoredTrips.replace(monitoredTrip.id, monitoredTrip);

        // Match on second attempt.
        assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, true, 1, TRIP_ACTIVE);
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, true, 0, TRIP_ACTIVE);

        // Match on third attempt.
        for (int i = 1; i <= 3; i++) {
            int expectedAttempts = (i < 3) ? i : 0;
            OtpResponse response = (i < 3) ? unexpectedResponse : expectedResponse;
            assertCheckMonitoredTrip(monitoredTrip, response, true, expectedAttempts, TRIP_ACTIVE);
        }

        int maxChecks = CheckMonitoredTrip.MAXIMUM_MONITORED_TRIP_ITINERARY_CHECKS;

        // Fail after maximum checks have been reached.
        for (int i = 1; i <= maxChecks; i++) {
            TripStatus tripStatus = (i < maxChecks) ? TRIP_ACTIVE : NEXT_TRIP_NOT_POSSIBLE;
            assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, true, i, tripStatus);
        }

        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    /**
     * Create mock OTP response and set the base times of the first itinerary to Monday, June 15, 2020.
     */
    private OtpResponse getMockOtpResponseJune15() {
        OtpResponse mockResponse = mockOtpPlanResponse();
        Itinerary mockMondayJune15Itinerary = firstItinerary(mockResponse);
        OtpTestUtils.setItineraryDay(mockMondayJune15Itinerary, 15);
        return mockResponse;
    }

    /**
     * Run check on monitored trip and confirm expected state.
     */
    private void assertCheckMonitoredTrip(
        MonitoredTrip monitoredTrip,
        OtpResponse mockResponse,
        boolean hasTolerantItineraryCheck,
        int expectedAttempts,
        TripStatus expectedTripStatus
    ) throws CloneNotSupportedException {
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, () -> mockResponse, hasTolerantItineraryCheck);
        checkMonitoredTrip.run();
        assertEquals(expectedAttempts, monitoredTrip.attemptsToGetMatchingItinerary);
        assertEquals(expectedTripStatus, monitoredTrip.journeyState.tripStatus);
    }

    @ParameterizedTest
    @MethodSource("createCanUnsnoozeTripCases")
    void canUnsnoozeTrip(ZonedDateTime lastCheckedTime, ZonedDateTime clockTime, boolean shouldUnsnooze) throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            OtpTestUtils.createDefaultJourneyState()
        );
        monitoredTrip.id = UUID.randomUUID().toString();
        // Mark trip as snoozed
        monitoredTrip.snoozed = true;
        // Set monitored days to Tuesday only.
        monitoredTrip.monday = false;
        monitoredTrip.tuesday = true;

        Persistence.monitoredTrips.create(monitoredTrip);

        // Mock the current time
        DateTimeUtils.useFixedClockAt(clockTime);

        // After snoozed trip is over, trip checks on that trip should not be skipped
        CheckMonitoredTrip check = new CheckMonitoredTrip(monitoredTrip, this::mockOtpPlanResponse);

        // Add artifacts of prior monitoring (e.g. monitoring was active until a few minutes before trip snooze)
        JourneyState journeyState = monitoredTrip.journeyState;
        journeyState.targetDate = "2020-06-09";
        journeyState.tripStatus = TripStatus.TRIP_UPCOMING;
        // Set last-checked-time
        journeyState.lastCheckedEpochMillis = lastCheckedTime.toInstant().toEpochMilli();
        journeyState.matchingItinerary = monitoredTrip.itinerary;
        check.previousJourneyState = journeyState;
        check.previousMatchingItinerary = monitoredTrip.itinerary;

        assertEquals(shouldUnsnooze, check.shouldUnsnoozeTrip());
        check.shouldSkipMonitoredTripCheck();

        MonitoredTrip modifiedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertEquals(!shouldUnsnooze, modifiedTrip.snoozed);

        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(modifiedTrip);
    }

    private static Stream<Arguments> createCanUnsnoozeTripCases() {
        // (Trips for these tests start on Tuesday, June 9, 2020 at 8:40am and ends at 8:58am.)
        ZonedDateTime tuesday = MONDAY_20200608_NOON.withDayOfMonth(9).withHour(0).withMinute(0).withSecond(0);
        ZonedDateTime wednesday = tuesday.withDayOfMonth(10);

        return Stream.of(
            // Trip snoozed at 8:00am on Tuesday, June 9, 2020, should remain snoozed right after trip ends at 9:00am.
            Arguments.of(tuesday.withHour(8), tuesday.withHour(9), false),
            // Trip snoozed at 8:00am on Tuesday, June 9, 2020, should unsnooze at 12:00am (midnight) on
            // Wednesday, June 10, 2020, but it is too early for the trip to be analyzed again.
            Arguments.of(tuesday.withHour(8), wednesday, true),
            // Trip snoozed on Monday, June 8, 2020 (a day before the trip starts), should unsnooze at 12:00am (midnight)
            // on Tuesday, June 9, 2020.
            Arguments.of(MONDAY_20200608_NOON, tuesday, true)
        );
    }

    @ParameterizedTest
    @MethodSource("createUpdateTripWithStaleStateCases")
    void canUpdateTripWithStaleState(
        ZonedDateTime clockTime,
        boolean isRecurring,
        TripStatus currentStatus,
        TripStatus expectedStatus,
        String message
    ) throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            OtpTestUtils.createDefaultJourneyState()
        );
        monitoredTrip.id = UUID.randomUUID().toString();
        // If recurring, set monitored days to Tuesday only.
        monitoredTrip.updateAllDaysOfWeek(false);
        if (isRecurring) {
            monitoredTrip.tuesday = true;
        }
        monitoredTrip.journeyState.targetDate = "2020-06-09";
        monitoredTrip.journeyState.tripStatus = currentStatus;

        Persistence.monitoredTrips.create(monitoredTrip);

        // Mock the current time
        DateTimeUtils.useFixedClockAt(clockTime);

        // After trip has completed, check that trip status has been updated.
        CheckMonitoredTrip check = new CheckMonitoredTrip(monitoredTrip, this::mockOtpPlanResponse);

        check.run();

        MonitoredTrip modifiedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertEquals(expectedStatus, modifiedTrip.journeyState.tripStatus, message);
    }

    private static Stream<Arguments> createUpdateTripWithStaleStateCases() {
        // (Trips for these tests start on Tuesday, June 9, 2020 at 8:40am and ends at 8:58am.)
        // The initial state for the trip is TRIP_ACTIVE.
        ZonedDateTime tuesday = MONDAY_20200608_NOON.withDayOfMonth(9).withHour(0).withMinute(0).withSecond(0);

        return Stream.of(
            Arguments.of(tuesday.withHour(8).withMinute(50), true, TRIP_ACTIVE, TRIP_ACTIVE, " During trip (before 8:58 am), state should remain active."),
            Arguments.of(tuesday.withHour(10), true, TRIP_ACTIVE, TRIP_UPCOMING, "After trip (after 9am), state should change to upcoming (for recurring trip)."),
            Arguments.of(tuesday.withHour(10), true, NEXT_TRIP_NOT_POSSIBLE, TRIP_UPCOMING, "Stale trip status should be updated to upcoming (for recurring trip)."),
            Arguments.of(tuesday.withHour(10), false, NEXT_TRIP_NOT_POSSIBLE, PAST_TRIP, "Stale trip status should be updated to past (for one-time trip)."),
            Arguments.of(tuesday.withHour(8), true, TRIP_ACTIVE, TRIP_UPCOMING, "Shortly before trip starts, state should change to upcoming."),
            Arguments.of(tuesday.withHour(4), true, TRIP_ACTIVE, TRIP_UPCOMING, "Long before trip starts, state should change to upcoming."),
            Arguments.of(tuesday.withHour(4), true, NO_LONGER_POSSIBLE, NO_LONGER_POSSIBLE, "Should not attempt to update a trip no longer possible.")
        );
    }

    @Test
    void testDuplicateNotifications() throws Exception {
        OtpUser observer = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-observer-user"));

        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            true,
            OtpTestUtils.createDefaultJourneyState()
        );
        monitoredTrip.primary = new MobilityProfileLite(user);
        monitoredTrip.observers.add(new RelatedUser(observer.email, RelatedUser.RelatedUserStatus.CONFIRMED));
        Persistence.monitoredTrips.replace(monitoredTrip.id, monitoredTrip);

        Leg expectedLeg = monitoredTrip.itinerary.legs.get(1);
        Coordinates expectedLegDestinationCoords = new Coordinates(expectedLeg.to);
        Leg nextLeg = monitoredTrip.itinerary.legs.get(2);

        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(expectedLeg)
            .setNextLeg(nextLeg)
            .setCurrentPosition(expectedLegDestinationCoords)
            .build();

        triggerCheckMonitoredTrip(monitoredTrip, travelerPosition);
        MonitoredTrip updated = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertNotEquals(-1, updated.journeyState.lastNotificationTimeMillis);
        long previousLastNotificationTimeMillis = updated.journeyState.lastNotificationTimeMillis;

        triggerCheckMonitoredTrip(monitoredTrip, travelerPosition);
        updated = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertEquals(previousLastNotificationTimeMillis, updated.journeyState.lastNotificationTimeMillis);

        Persistence.monitoredTrips.removeById(monitoredTrip.id);
        Persistence.otpUsers.removeById(observer.id);
    }

    private void triggerCheckMonitoredTrip(MonitoredTrip monitoredTrip, TravelerPosition travelerPosition) throws CloneNotSupportedException {
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, this::mockOtpPlanResponse);
        checkMonitoredTrip.IS_TEST = true;
        checkMonitoredTrip.targetZonedDateTime = monitoredTrip.tripZonedDateTime(DateTimeUtils.nowAsLocalDate());
        checkMonitoredTrip.processLegTransition(NotificationType.MODE_CHANGE_NOTIFICATION, travelerPosition);
    }

    /**
     * Edge case when UTC time is 1 day ahead of local time. If this is failing, ensure OTP_TIMEZONE is set in env.yml
     */
    @Test
    void testCheckMonitoredTripWhenUTCIsNextDay() throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            OtpTestUtils.createDefaultJourneyState()
        );

        // monitored trip start time = 1:30AM UTC or 5:30PM PST
        OtpTestUtils.updateBaseItineraryTime(
            monitoredTrip.itinerary,
            DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime)
                .withHour(17)
                .withMinute(30)
                .withZoneSameInstant(DateTimeUtils.getOtpZoneId())
        );
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.tripTime = "17:30";
        monitoredTrip.leadTimeInMinutes = 30;
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);

        // Set up an OTP mock response in order to trigger some of the monitor checks.
        OtpResponse mockResponse = mockOtpPlanResponse();
        Itinerary mockTuesdayJune09Itinerary = firstItinerary(mockResponse);

        // itinerary start time = 1:30AM UTC or 5:30PM PST
        OtpTestUtils.updateBaseItineraryTime(
            mockTuesdayJune09Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockTuesdayJune09Itinerary.startTime)
                .withHour(17)
                .withMinute(30)
                .withZoneSameInstant(DateTimeUtils.getOtpZoneId())
        );

        // change "now" time after initial check to be within 30 min lead
        // 1:00AM UTC or 5:00PM PST
        DateTimeUtils.useFixedClockAt(
            MONDAY_20200608_NOON
                .withDayOfMonth(9)
                .withHour(17)
                .withMinute(0)
                .withZoneSameInstant(DateTimeUtils.getOtpZoneId())
        );

        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, () -> mockResponse);
        checkMonitoredTrip.run();

        // trip should have been skipped
        Assertions.assertEquals(DayOfWeek.TUESDAY, checkMonitoredTrip.targetZonedDateTime.getDayOfWeek());

        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }
}