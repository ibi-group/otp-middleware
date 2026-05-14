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
import org.junit.jupiter.params.provider.ValueSource;
import org.opentripplanner.middleware.itinerarymatching.ItineraryMatcher;
import org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils;
import org.opentripplanner.middleware.itinerarymatching.LegIdProcessor;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MobilityProfileLite;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Alert;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.tripmonitor.TripStatus;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import static org.opentripplanner.middleware.models.TripMonitorNotification.createItineraryNotFoundNotification;
import static org.opentripplanner.middleware.testutils.OtpTestUtils.createDefaultJourneyState;
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
    public static final ZonedDateTime TUESDAY_20200609 = MONDAY_20200608_NOON
        .withDayOfMonth(9)
        .withHour(0)
        .withMinute(0)
        .withSecond(0);
    public static final ZonedDateTime TUESDAY_20200609_0800 = TUESDAY_20200609.withHour(8);
    public static final ZonedDateTime TUESDAY_20200609_0850 = TUESDAY_20200609_0800.withMinute(50);
    private static final ZonedDateTime MONDAY_20200615_0845 = MONDAY_20200608_NOON
        .withDayOfMonth(15)
        .withHour(8)
        .withMinute(45);
    private static final ZonedDateTime MONDAY_20200615_0835 = MONDAY_20200615_0845.withMinute(35);

    @BeforeAll
    static void setup() {
        user = PersistenceTestUtils.createUser("user@example.com");
    }

    @AfterAll
    static void tearDown() {
        user.delete(false);
    }

    @AfterEach
    void tearDownAfterTest() {
        DateTimeUtils.useSystemDefaultClockAndTimezone();
    }

    /** Provides a mock OTP 'plan' response */
    public static OtpResponse mockOtpPlanResponse() {
        return mockOtpPlanResponse(null);
    }

    public static OtpResponse mockOtpPlanResponse(OtpRequest ignored) {
        try {
            // Setup an OTP mock response in order to trigger some of the monitor checks.
            return OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE_LEGID.getResponse();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /** Provides a mock OTP 'plan' response for trip queried at midnight */
    public OtpResponse mockOtpPlanResponseForTripQueriedAtMidnight() {
        try {
            // Setup an OTP mock response in order to trigger some of the monitor checks.
            return OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE_TRIP_QUERIED_AT_MIDNIGHT.getResponse();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void canMonitorTripAtMidnightLastCheckedLongTimeAgo() throws Exception {
        // Mock OTP response matching test case.
        OtpResponse mockResponse = mockOtpPlanResponseForTripQueriedAtMidnight();

        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE_TRIP_QUERIED_AT_MIDNIGHT.clone(),
            false,
            null
        );

        final ZonedDateTime midnightFourWeeksLater = DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime)
            .plusDays(28)
            .withHour(0)
            .withMinute(0)
            .withSecond(17);
        DateTimeUtils.useFixedClockAt(midnightFourWeeksLater);

        monitoredTrip.recomputeTargetDateAndAdjustMatchingItinerary();
        Persistence.monitoredTrips.create(monitoredTrip);

        CheckMonitoredTrip checkMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(mockResponse));
        checkMonitoredTrip.run();

        assertTrue(checkMonitoredTrip.notifications.isEmpty());
        assertEquals(TRIP_UPCOMING, checkMonitoredTrip.journeyState.tripStatus);
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    @ParameterizedTest
    @MethodSource("tripEndingPastMidnightCases")
    void canMonitorTripEndingPastMidNight(
        String startDate,
        String startTime,
        String endDate,
        String endTime,
        String targetDate
    ) throws Exception {
        MonitoredTrip trip = new MonitoredTrip();

        Itinerary itinerary = new Itinerary();
        ZonedDateTime start = DateTimeUtils.makeOtpZonedDateTime(startDate, startTime);
        ZonedDateTime end = DateTimeUtils.makeOtpZonedDateTime(endDate, endTime);
        itinerary.startTime = Date.from(start.toInstant());
        itinerary.endTime = Date.from(end.toInstant());
        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";
        walkLeg.from = new Place();
        walkLeg.to = new Place();
        itinerary.legs = List.of(walkLeg);
        trip.itinerary = itinerary;
        trip.updateAllDaysOfWeek(true);
        trip.itineraryExistence = new ItineraryExistence();
        trip.itineraryExistence.wednesday = new ItineraryExistence.ItineraryExistenceResult();
        trip.itineraryExistence.thursday = new ItineraryExistence.ItineraryExistenceResult();

        DateTimeUtils.useFixedClockAt(start.minusMinutes(10));

        Persistence.monitoredTrips.create(trip);
        OtpResponse response = new OtpResponse();
        response.plan = new TripPlan();
        response.plan.itineraries = List.of(itinerary);
        CheckMonitoredTrip checkMonitoredTrip = tripChecker(trip, firstItinerary(response));
        checkMonitoredTrip.run();

        MonitoredTrip updated = Persistence.monitoredTrips.getById(trip.id);
        assertEquals(TRIP_UPCOMING, checkMonitoredTrip.journeyState.tripStatus);
        assertEquals(targetDate, updated.journeyState.targetDate);
        PersistenceTestUtils.deleteMonitoredTrip(trip);
    }

    private static Stream<Arguments> tripEndingPastMidnightCases() {
        return Stream.of(
            // Wednesday
            Arguments.of("2025-10-01", "23:50", "2025-10-02", "00:00", "2025-10-01"),
            // Thursday
            Arguments.of("2025-10-02", "00:00", "2025-10-02", "00:15", "2025-10-02"),
            // Overlap
            Arguments.of("2025-10-01", "23:50", "2025-10-02", "00:15", "2025-10-01")
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-4000, -60, 60})
    void canMonitorAlertsInUpcomingOrOngoingTrip(int offsetSeconds) throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);

        // Simulate legs and add fake alerts.
        OtpResponse mockResponse = mockOtpPlanResponse();
        Itinerary itinerary = firstItinerary(mockResponse);
        itinerary.legs.get(1).alerts = Lists.newArrayList(new Alert());

        // Mock the current time to be before/during the trip.
        DateTimeUtils.useFixedClockAt(
            ZonedDateTime.ofInstant(itinerary.startTime.toInstant().plusSeconds(offsetSeconds),
                DateTimeUtils.getOtpZoneId())
        );

        CheckMonitoredTrip checkMonitoredTrip = tripChecker(monitoredTrip, itinerary);
        checkMonitoredTrip.run();

        // Assert that there is one notification generated during check and it is an alert.
        assertEquals(1, checkMonitoredTrip.notifications.size());
        assertEquals(NotificationType.ALERT_FOUND, checkMonitoredTrip.notifications.iterator().next().type);
        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    @Test
    void sendInitialReminderNotificationForOneTimeTrip() throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();

        // Set one time trip state.
        monitoredTrip.updateAllDaysOfWeek(false);
        monitoredTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;
        Persistence.monitoredTrips.create(monitoredTrip);

        // Mock time to be an hour or so before the trip start.
        DateTimeUtils.useFixedClockAt(DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime).minusMinutes(70));

        CheckMonitoredTrip checkMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(mockOtpPlanResponse()));
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
        // initialize JourneyState time values 
        journeyState.scheduledArrivalTimeEpochMillis = journeyState.matchingItinerary.endTime.getTime();
        journeyState.scheduledDepartureTimeEpochMillis = journeyState.matchingItinerary.startTime.getTime();
        journeyState.baselineArrivalTimeEpochMillis = journeyState.matchingItinerary.endTime.getTime();
        journeyState.baselineDepartureTimeEpochMillis = journeyState.matchingItinerary.startTime.getTime();

        if (notificationType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY || notificationType == NotificationType.DEPARTURE_DELAY) {
            journeyState.baselineDepartureTimeEpochMillis += previousDelayMillis;
            journeyState.hasRealtimeData = true;
        }
        if (notificationType == NotificationType.DEPARTURE_AND_ARRIVAL_DELAY || notificationType == NotificationType.ARRIVAL_DELAY) {
            journeyState.baselineArrivalTimeEpochMillis += previousDelayMillis;
            journeyState.hasRealtimeData = true;
        }

        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.journeyState = journeyState;
        CheckMonitoredTrip check = tripChecker(monitoredTrip, firstItinerary(mockOtpPlanResponse()));
        check.matchingItinerary = OtpTestUtils.createDefaultItinerary();

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

    /**
     * Ensure that journey state time values are properly initialized after making OTP request
     * and setting the matchingItinerary, so that delays are correctly computed.
     */
    @Test
    void testJourneyStateAfterOTPRequest() throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        Persistence.monitoredTrips.create(monitoredTrip);

        // Mock the current time to a few minutes after trip start.
        DateTimeUtils.useFixedClockAt(DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime).plusMinutes(10));

        Itinerary itineraryForLegQuery = firstItinerary(mockOtpPlanResponse());

        CheckMonitoredTrip check = tripChecker(monitoredTrip, itineraryForLegQuery);

        // set trip status to be upcoming
        monitoredTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        check.targetZonedDateTime = DateTimeUtils.makeOtpZonedDateTime(itineraryForLegQuery.startTime);

        // Execute checkOtpAndUpdateTripStatus method and verify the expected outcome.
        assertTrue(check.checkOtpAndUpdateTripStatus());
        assertEquals(TripStatus.TRIP_ACTIVE, monitoredTrip.journeyState.tripStatus);

        // There should be no notifications.
        assertNull(check.checkTripForDelays());
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
                " Your trip is now predicted to depart 20 minutes late at 8:49[\\u202f ]AM \\(Now arriving at 9:18[\\u202f ]AM\\)\\.",
                "20m-late trip previously on-time => show dep/arr delay notifications"
            ),
            Arguments.of(
                20,
                0,
                NotificationType.DEPARTURE_DELAY,
                STOPWATCH_ICON +
                " Your trip is now predicted to depart 20 minutes late \\(at 8:49[\\u202f ]AM\\)\\.",
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
                " Your trip is now predicted to depart 18 minutes early at 8:11[\\u202f ]AM \\(Now arriving at 8:40[\\u202f ]AM\\)\\.",
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
                " Your trip is now predicted to depart about on time at 8:29[\\u202f ]AM \\(Now arriving at 8:58[\\u202f ]AM\\)\\.",
                "On-time trip previously late => show on-time notifications"
            )
        );
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
            OtpTestUtils.createDefaultJourneyState(firstItinerary(OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.getResponse()))
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
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        Persistence.monitoredTrips.create(monitoredTrip);

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        CheckMonitoredTrip mockCheckMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(getMockOtpResponseJune15()));

        // create mock itinerary existence for trip
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not found on Mondays as of the last check
        monitoredTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        monitoredTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetZonedDateTime = MONDAY_20200615_0835;

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertTrue(mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);

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
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        Persistence.monitoredTrips.create(monitoredTrip);

        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();

        CheckMonitoredTrip mockCheckMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(mockWeekdayResponse));

        // create mock itinerary existence for trip that indicates the trip was
        // still possible on Mondays as of the last check
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // set trip status to be upcoming
        monitoredTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // Initialize some positive number of attempts to obtain a matching itinerary.
        // That number should be reset when the NEXT_TRIP_NOT_POSSIBLE state is set.
        monitoredTrip.attemptsToGetMatchingItinerary = 5;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetZonedDateTime = MONDAY_20200615_0835;

        // Assume that an unmonitorable trip notification has been previously sent
        // to ensure a new notification is sent anyways.
        mockCheckMonitoredTrip.previousJourneyState.lastNotifications.add(
            createItineraryNotFoundNotification(true, Locale.US)
        );

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
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);

        // verify that status is active
        assertEquals(
            NEXT_TRIP_NOT_POSSIBLE,
            updatedTrip.journeyState.tripStatus,
            "updated trips status should indicate trip could not be monitored this day"
        );

        // verify itinerary existence was NOT updated to show trip could not be monitored today
        assertTrue(
            updatedTrip.itineraryExistence.monday.isValid(),
            "updated Trip should remain valid on Monday"
        );

        // Check that the trip was snoozed. This is to avoid repeated unsuccessful network calls.
        assertTrue(mockCheckMonitoredTrip.trip.snoozed, "Trip should be snoozed if no matching itinerary.");

        assertEquals(
            0,
            mockCheckMonitoredTrip.trip.attemptsToGetMatchingItinerary,
            "Attempts to obtain a matching itinerary should have been reset."
        );

        assertEquals(
            1,
            mockCheckMonitoredTrip.notifications.size(),
            "A notification should be generated for the next trip could not be monitored"
        );
        assertEquals(
            "Unable to monitor trip. Monitoring snoozed for today after multiple failed attempts to locate your itinerary. Go to Trip Details for options.",
            mockCheckMonitoredTrip.notifications.iterator().next().body,
            "The notification should have the appropriate message when the next trip could not be monitored"
        );
        assertFalse(
            mockCheckMonitoredTrip.thereAreNoNewNotifications(),
            "Unmonitorable trip notification should be sent even if one was previously sent."
        );
    }

    /**
     * Tests whether an OTP request can be made and if the trip is properly updated after not being able to find a
     * matching itinerary for all days of the week.
     */
    @Test
    void canMakeOTPRequestAndResolveNoLongerPossibleTrip() throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        Persistence.monitoredTrips.create(monitoredTrip);

        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        CheckMonitoredTrip mockCheckMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(mockWeekdayResponse));

        // create mock itinerary existence for trip for Mondays
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not possible on Mondays as of the last check
        monitoredTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        monitoredTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

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
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);

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
     * Utility method to set delays on a transit itinerary.
     */
    void addTransitLegDelay(Itinerary itinerary, int departureDelay, int arrivalDelay, boolean realTime) {
        Leg walkLeg = itinerary.legs.get(0);
        Leg transitLeg = itinerary.legs.get(1);
        Leg finalLeg = itinerary.legs.get(2);

        walkLeg.offsetTimes(Duration.ofSeconds(departureDelay).toMillis());

        transitLeg.realTime = realTime;
        transitLeg.departureDelay += departureDelay;
        transitLeg.startTime = Date.from(transitLeg.startTime.toInstant().plusSeconds(departureDelay));
        transitLeg.arrivalDelay += arrivalDelay;
        transitLeg.endTime = Date.from(transitLeg.endTime.toInstant().plusSeconds(arrivalDelay));

        finalLeg.offsetTimes(Duration.ofSeconds(arrivalDelay).toMillis());

        itinerary.startTime = Date.from(itinerary.startTime.toInstant().plusSeconds(departureDelay));
        itinerary.endTime = Date.from(itinerary.endTime.toInstant().plusSeconds(arrivalDelay));
    }

    /**
     * A trip delay notification should be sent when saving a trip that has delays,
     * and it is possible to find a matching trip without delays with the trip time in the OTP query params.
     * Once the trip is over, no notifications should be sent.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void canSendDelayNotifications(boolean isOneTime) throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.itinerary.clearAlerts();
        monitoredTrip.journeyState = OtpTestUtils.createDefaultJourneyState();
        monitoredTrip.journeyState.matchingItinerary.clearAlerts();

        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        Itinerary firstMockItinerary = firstItinerary(mockWeekdayResponse);
        firstMockItinerary.clearAlerts();

        CheckMonitoredTrip mockCheckMonitoredTrip = tripChecker(monitoredTrip, firstMockItinerary);

        // Override matching itinerary to null to simulate initial save.
        mockCheckMonitoredTrip.matchingItinerary = null;
        // Trigger notifications for 5-minute delays instead of 15.
        monitoredTrip.departureVarianceMinutesThreshold = 5;
        monitoredTrip.arrivalVarianceMinutesThreshold = 5;
        if (isOneTime) monitoredTrip.updateAllDaysOfWeek(false);
        // Add delays to the original saved trip, different from the mock response.
        // The delays from the mock response should be used.
        addTransitLegDelay(monitoredTrip.itinerary, 600, 720, true);

        Persistence.monitoredTrips.create(monitoredTrip);

        // create mock itinerary existence for trip for Tuesdays
        monitoredTrip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();

        ZonedDateTime beforeTripStart = DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime).minusMinutes(30);
        ZonedDateTime transitLegEnd = DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.legs.get(1).endTime);
        ZonedDateTime afterTripEnds = DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.endTime).plusMinutes(3);

        List<DelayCase> cases = List.of(
            // TODO: fix time separator char
            // Add some delays for the trip.
            new DelayCase(300, 420, true, beforeTripStart, 1, "⏱ Your trip is now predicted to depart 5 minutes late (at 8:34 AM)."),
            // Decrease real-time delays (subtract delays) from the OTP response.
            new DelayCase(-100, -60, true, beforeTripStart, 1, "⏱ Your trip is now predicted to arrive 6 minutes late (at 9:04 AM)."),
            // Drop real-time updates (subtract delays) from the OTP response.
            new DelayCase(-200, -360, false, beforeTripStart, 1, "⏱ Real-time updates for your trip were lost. Monitoring will be based on your originally saved trip."),

            // Add back delays for the trip.
            new DelayCase(300, 420, true, beforeTripStart, 1, "⏱ Your trip is now predicted to depart 5 minutes late (at 8:34 AM)."),
            // OTP drops real-time updates at/after the end of the transit leg.
            // No notifications should be sent when the transit leg is over.
            new DelayCase(
                -300,
                -420,
                false,
                transitLegEnd.plusMinutes(2),
                0,
                null
            ),
            // No notifications should be sent when the trip is considered over and the next trip is upcoming.
            // (Next day is simulated by keeping the same mock itinerary and shifting the clock back to after the trip the previous day.)
            new DelayCase(
                0,
                0,
                false,
                isOneTime ? beforeTripStart.plusHours(1) : afterTripEnds.minusDays(1),
                0,
                null
            )
        );

        for (DelayCase c : cases) {
            DateTimeUtils.useFixedClockAt(c.clockTime);
            addTransitLegDelay(firstMockItinerary, c.departureDelay, c.arrivalDelay, c.isRealTime);

            // Update mocked legs for the new clock time
            mockCheckMonitoredTrip.updateLegFinderForTests(new LegFinder(
                new MockLegResponseProvider(firstMockItinerary, leg -> LegIdProcessor.computeLegIdForServiceDate(leg, DateTimeUtils.nowAsLocalDate()))::getLegResponse,
                LegIdProcessor::computeLegIdForServiceDate
            ));

            // Clear previous notifications to ensure expected notifications are recorded.
            mockCheckMonitoredTrip.notifications.clear();

            mockCheckMonitoredTrip.run();

            ItineraryMatcher itineraryMatcher = new ItineraryMatcher(firstMockItinerary, mockCheckMonitoredTrip.matchingItinerary);
            assertTrue(itineraryMatcher.match(), itineraryMatcher.getFailingReason());

            assertEquals(c.expectedNotifications, mockCheckMonitoredTrip.notifications.size());

            if (c.expectedNotifications == 1) {
                assertEquals(
                    c.message,
                    mockCheckMonitoredTrip.notifications.iterator().next().body,
                    "The notification text should be correct."
                );
            }
        }
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
        boolean skipMondayTuesday,
        boolean itineraryExistsInOtp
    ) throws Exception {
        DateTimeUtils.useFixedClockAt(nowTime);

        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        Date targetItineraryStartTime = null;

        ZonedDateTime mockLegDay = nowTime.withDayOfMonth(skipMondayTuesday && tripStatus != TRIP_ACTIVE ? 10 : 8);
        if (itineraryExistsInOtp) {
            // Create an OTP mock to return for the next target date, with itinerary start on Monday, June 8, 2020.
            Itinerary firstItinerary = firstItinerary(mockWeekdayResponse);
            OtpTestUtils.updateBaseItineraryTime(
                firstItinerary,
                DateTimeUtils.makeOtpZonedDateTime(firstItinerary.startTime)
                    .withYear(2020)
                    .withMonth(6)
                    .withDayOfMonth(skipMondayTuesday && tripStatus != TRIP_ACTIVE ? 10 : 8)
            );
            targetItineraryStartTime = firstItinerary.startTime;
        } else {
            // If no itineraries exist, empty the list of legs in the first itinerary.
            firstItinerary(mockWeekdayResponse).legs = new ArrayList<>();
        }

        // Create an OTP mock to return, with itinerary start on Tuesday, June 9, 2020,
        // unless the trip is still active in which case we set the trip day to Monday, June 8, 2020.
        OtpResponse mockPreviousWeekdayResponse = mockOtpPlanResponse();
        Itinerary mockPreviousItinerary = firstItinerary(mockPreviousWeekdayResponse);
        OtpTestUtils.updateBaseItineraryTime(
            mockPreviousItinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockPreviousItinerary.startTime)
                .withYear(2020)
                .withMonth(6)
                .withDayOfMonth(tripStatus == TRIP_ACTIVE ? 8 : 9)
        );

        Itinerary targetItinerary = null;
        if (itineraryExistsInOtp) {
            targetItinerary = firstItinerary(mockWeekdayResponse);
            // Make sure that the start time on the original trip was not changed inadvertently.
            assertEquals(targetItineraryStartTime, targetItinerary.startTime);
        }

        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        CheckMonitoredTrip mockCheckMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(mockWeekdayResponse), mockLegDay.toLocalDate());
        mockCheckMonitoredTrip.matchingItinerary = OtpTestUtils.createDefaultItinerary();

        if (skipMondayTuesday) {
            // All days are initially monitored.
            // For some cases, un-monitor Monday and Tuesday, so that the next trip date is Wednesday.
            monitoredTrip.monday = false;
            monitoredTrip.tuesday = false;
        }

        // The trip exists Monday, Tuesday, and Wednesday.
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.itineraryExistence.tuesday = new ItineraryExistence.ItineraryExistenceResult();
        monitoredTrip.itineraryExistence.wednesday = new ItineraryExistence.ItineraryExistenceResult();

        // Use a previously computed trip status with the specified currentTargetDate.
        // Copy those journey state params, including to the CheckMonitoredTrip object too.
        monitoredTrip.journeyState.tripStatus = tripStatus;
        monitoredTrip.journeyState.targetDate = currentTargetDate;
        monitoredTrip.journeyState.matchingItinerary = mockPreviousItinerary;
        mockCheckMonitoredTrip.previousJourneyState = new JourneyState();
        mockCheckMonitoredTrip.previousJourneyState.targetDate = monitoredTrip.journeyState.targetDate;
        mockCheckMonitoredTrip.previousJourneyState.tripStatus = monitoredTrip.journeyState.tripStatus;
        mockCheckMonitoredTrip.previousJourneyState.matchingItinerary = mockPreviousItinerary;
        mockCheckMonitoredTrip.previousMatchingItinerary = mockPreviousItinerary;

        // Set the current target date/time.
        mockCheckMonitoredTrip.targetZonedDateTime = mockLegDay.withDayOfMonth(previousTargetDay);

        Persistence.monitoredTrips.create(monitoredTrip);

        // Perform the skip check (this populates some internal states).
        // This trip should not be skipped if Monday and Tuesday are monitored because,
        // for those cases, the trip occurs within minutes of the mocked system time.
        assertEquals(skipMondayTuesday, mockCheckMonitoredTrip.shouldSkipMonitoredTripCheck());

        assertEquals(
            tripStatus == TRIP_ACTIVE,
            mockCheckMonitoredTrip.trip.tripTargetDateIsConsistentWithMatchingItinerary()
        );

        // Execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome.
        assertEquals(itineraryExistsInOtp, mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // Fetch updated trip from persistence and check the trip status and target date.
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);

        if (!skipMondayTuesday) {
            assertTrue(updatedTrip.monday);
        }

        assertEquals(
            itineraryExistsInOtp ? tripStatus : NEXT_TRIP_NOT_POSSIBLE,
            updatedTrip.journeyState.tripStatus,
             tripStatus == TRIP_UPCOMING
                 ? "Trip state should remain in future."
                 : "Active trips will continue to be monitored until they end. Failed queries on a different target date should result in next trip not possible state."
        );

        assertEquals(!itineraryExistsInOtp, updatedTrip.snoozed);

        assertEquals(
            expectedTargetDate,
            updatedTrip.journeyState.targetDate,
            "Trip target date should have been changed according to monitored days."
        );

        assertEquals(
            targetItinerary == null, updatedTrip.journeyState.matchingItinerary == null
        );

        if (targetItinerary != null) {
            assertEquals(
                targetItinerary.startTime,
                updatedTrip.journeyState.matchingItinerary.startTime,
                "A matching itinerary should have been provided."
            );
        }
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
                true,
                true
            ),
            Arguments.of(
                // Mock the current time to same day of itinerary, between itinerary start and end time.
                monday0830.withMinute(50),
                8,
                "2020-06-08",
                "2020-06-08",
                TRIP_ACTIVE,
                true,
                true
            ),
            Arguments.of(
                monday0830.minusMinutes(10),
                9,
                "2020-06-09",
                "2020-06-08",
                TRIP_UPCOMING,
                // Trip check should not be skipped because the trip occurs within minutes of the mocked system time.
                false,
                true
            ),
            Arguments.of(
                monday0830,
                8,
                "2020-06-08",
                "2020-06-10",
                TRIP_UPCOMING,
                true,
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
        MonitoredTrip trip = monitoredTripWithLegId();

        // Set the clock to an hour before trip start, the day following the saved trip above.
        ZonedDateTime dayAfter = DateTimeUtils.makeOtpZonedDateTime(trip.itinerary.startTime).plusDays(1);
        DateTimeUtils.useFixedClockAt(dayAfter.minusMinutes(70));

        Itinerary itineraryForOtpLegQuery = firstItinerary(mockOtpPlanResponse());
        OtpTestUtils.updateBaseItineraryTime(itineraryForOtpLegQuery, dayAfter);

        // Set the matching itinerary and previous matching itinerary to Wednesday
        trip.journeyState = createDefaultJourneyState();
        trip.journeyState.matchingItinerary = firstItinerary(mockOtpPlanResponse());
        ZonedDateTime twoDaysAfter = dayAfter.plusDays(1);
        OtpTestUtils.updateBaseItineraryTime(trip.journeyState.matchingItinerary, twoDaysAfter);
        assertEquals(
            twoDaysAfter.toLocalDate(),
            DateTimeUtils.makeOtpZonedDateTime(trip.journeyState.matchingItinerary.startTime).toLocalDate()
        );

        trip.journeyState.lastCheckedEpochMillis = DateTimeUtils.nowAsZonedDateTime().minusMinutes(30).toInstant().toEpochMilli();

        // Set target date to Wednesday
        trip.journeyState.targetDate = twoDaysAfter.toLocalDate().toString();

        Persistence.monitoredTrips.create(trip);

        // Run the checks. Test the new target date
        CheckMonitoredTrip check = tripChecker(trip, itineraryForOtpLegQuery);
        check.run();

        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(trip.id);

        assertEquals(
            dayAfter.toLocalDate().toString(),
            updatedTrip.journeyState.targetDate
        );
        assertEquals(
            dayAfter.toLocalDate(),
            DateTimeUtils.makeOtpZonedDateTime(updatedTrip.journeyState.matchingItinerary.startTime).toLocalDate()
        );
    }

    @Test
    void shouldReportOneTimeTripInPastAsCompleted() throws CloneNotSupportedException {
        MonitoredTrip trip = makeMonitoredTripFromNow(-900, -300);
        trip.journeyState.matchingItinerary = trip.itinerary;
        trip.journeyState.tripStatus = TRIP_ACTIVE;

        tripChecker(trip, firstItinerary(mockOtpPlanResponse())).checkOtpAndUpdateTripStatus();
        assertEquals(PAST_TRIP, trip.journeyState.tripStatus);
    }

    @Test
    void shouldReportOneTimeTripInPastWithTrackingAsActive() throws CloneNotSupportedException {
        MonitoredTrip trip = createPastActiveTripWithTrackedJourney();

        tripChecker(trip, firstItinerary(mockOtpPlanResponse())).checkOtpAndUpdateTripStatus();
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
        trip.itinerary.legs = List.of(
            ItineraryMatchingUtils.createBusLeg(
                "bus-leg",
                DateTimeUtils.convertToLocalDateTime(trip.itinerary.startTime),
                DateTimeUtils.convertToLocalDateTime(trip.itinerary.endTime)
            )
        );
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

        // Build fake OTP response, using an existing one as template.
        // Set the start time of that itinerary to "tomorrow" because "today"'s trip is over.
        Itinerary adjustedItinerary = trip.itinerary.clone();
        adjustedItinerary.offsetTimes(ONE_DAY_IN_MILLIS);

        CheckMonitoredTrip check = new CheckMonitoredTrip(
            trip,
            CheckMonitoredTripTest::mockOtpPlanResponse,
            new LegFinder(
                new MockLegResponseProvider(adjustedItinerary, ignored -> "bus-leg-updated")::getLegResponse,
                (leg, ignored) -> "bus-leg-updated"
            )
        );
        // Trip is advanced to next monitored day because "today"'s trip instance has ended.
        // As a result, trip status is set to upcoming, checkOtpAndUpdateTripStatus is skipped.
        assertTrue(check.shouldSkipMonitoredTripCheck());
        assertTrue(check.checkOtpAndUpdateTripStatus());

        assertEquals(TripStatus.TRIP_UPCOMING, check.journeyState.tripStatus);
        assertEquals(TripStatus.TRIP_UPCOMING, trip.journeyState.tripStatus);
        assertEquals(getShiftedDay(trip.itinerary.startTime, 1), trip.journeyState.targetDate);
    }

    @Test
    void shouldReportRecurringTripInstanceInPastWithTrackingAsActive() throws Exception {
        MonitoredTrip trip = createPastActiveTripWithTrackedJourney();
        setRecurringTodayAndTomorrow(trip);
        String todayFormatted = trip.journeyState.targetDate;

        CheckMonitoredTrip check = tripChecker(trip, firstItinerary(mockOtpPlanResponse()));
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
    void canMatchOnPlanQueryFallback() throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        Persistence.monitoredTrips.create(monitoredTrip);

        OtpResponse expectedResponse = getMockOtpResponseJune15();
        OtpResponse unexpectedResponse = getMockOtpResponseJune15();
        // Remove the transit leg (at index 1) so that the matching itineraries check fails.
        firstItinerary(unexpectedResponse).legs.remove(1);

        // Mock the current time to be 8:45am on Monday, June 15, 2020.
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // Match on first attempts.
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, true, 0, TRIP_ACTIVE);
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, false, 0, TRIP_ACTIVE);
        // Match using plan query fallback.
        assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, false, 0, TRIP_ACTIVE, true);
    }

    @Test
    void canBeTolerantWithItineraryChecks() throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        Persistence.monitoredTrips.create(monitoredTrip);

        OtpResponse expectedResponse = getMockOtpResponseJune15();
        OtpResponse unexpectedResponse = getMockOtpResponseJune15();
        // Remove the transit leg (at index 1) so that the matching itineraries check fails.
        firstItinerary(unexpectedResponse).legs.remove(1);

        // Mock the current time to be 8:45am on Monday, June 15, 2020.
        DateTimeUtils.useFixedClockAt(MONDAY_20200615_0845);

        // Match on first attempts.
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, true, 0, TRIP_ACTIVE);
        assertCheckMonitoredTrip(monitoredTrip, expectedResponse, false, 0, TRIP_ACTIVE);

        // Fail on first attempt.
        assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, false, 0, NEXT_TRIP_NOT_POSSIBLE);

        // Unsnooze trip (the call to CheckMonitoredTrip.run() will recalculate trip status, in this case TRIP_ACTIVE).
        monitoredTrip.snoozed = false;
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
            if (i < maxChecks) {
                assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, true, i, TRIP_ACTIVE);
            } else {
                // When NEXT_TRIP_NOT_POSSIBLE is set, the attempts to get a matching itinerary is reset.
                assertCheckMonitoredTrip(monitoredTrip, unexpectedResponse, true, 0, NEXT_TRIP_NOT_POSSIBLE);
            }
        }

        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    /**
     * Create mock OTP response and set the base times of the first itinerary to Monday, June 15, 2020.
     */
    private OtpResponse getMockOtpResponseJune15() {
        OtpResponse mockResponse = mockOtpPlanResponse();
        OtpTestUtils.updateBaseItineraryTime(
            firstItinerary(mockResponse),
            MONDAY_20200615_0845
        );
        return mockResponse;
    }

    /**
     * Shortcut for method with default mockOtpPlanResponse value of null.
     */
    private void assertCheckMonitoredTrip(
        MonitoredTrip monitoredTrip,
        OtpResponse mockResponse,
        boolean hasTolerantItineraryCheck,
        int expectedAttempts,
        TripStatus expectedTripStatus
    ) throws CloneNotSupportedException {
        assertCheckMonitoredTrip(
            monitoredTrip,
            mockResponse,
            hasTolerantItineraryCheck,
            expectedAttempts,
            expectedTripStatus,
            false
        );
    }

    /**
     * Run check on monitored trip and confirm expected state.
     */
    private void assertCheckMonitoredTrip(
        MonitoredTrip monitoredTrip,
        OtpResponse mockResponse,
        boolean hasTolerantItineraryCheck,
        int expectedAttempts,
        TripStatus expectedTripStatus,
        boolean mockOtpPlanResponse
    ) throws CloneNotSupportedException {
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(
            monitoredTrip,
            mockOtpPlanResponse ? CheckMonitoredTripTest::mockOtpPlanResponse : null,
            new LegFinder(
                new MockLegResponseProvider(firstItinerary(mockResponse), leg -> LegIdProcessor.computeLegIdForServiceDate(leg, DateTimeUtils.nowAsLocalDate()))::getLegResponse,
                LegIdProcessor::computeLegIdForServiceDate
            ),
            hasTolerantItineraryCheck
        );
        checkMonitoredTrip.run();
        assertEquals(expectedAttempts, monitoredTrip.attemptsToGetMatchingItinerary);
        assertEquals(expectedTripStatus, monitoredTrip.journeyState.tripStatus);
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
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.journeyState = OtpTestUtils.createDefaultJourneyState();
        ZonedDateTime legDay = DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime)
            .withYear(2020)
            .withMonth(6)
            .withDayOfMonth(9);
        OtpTestUtils.updateBaseItineraryTime(
            monitoredTrip.itinerary,
            legDay
        );
        OtpTestUtils.updateBaseItineraryTime(
            monitoredTrip.journeyState.matchingItinerary,
            legDay
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
        CheckMonitoredTrip check = tripChecker(monitoredTrip, firstItinerary(mockOtpPlanResponse()), legDay.toLocalDate());
        check.run();

        MonitoredTrip modifiedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertEquals(expectedStatus, modifiedTrip.journeyState.tripStatus, message);
    }

    private static Stream<Arguments> createUpdateTripWithStaleStateCases() {
        // Mock OTP trip for these tests start on Tuesday, June 9, 2020 at 8:40am and ends at 8:58am.
        // The current clock is set so that the next possible trip is as above.
        return Stream.of(
            Arguments.of(TUESDAY_20200609_0850, true, TRIP_ACTIVE, TRIP_ACTIVE, "During trip (before 8:58 am), state should remain active."),
            Arguments.of(TUESDAY_20200609_0850, true, NEXT_TRIP_NOT_POSSIBLE, TRIP_ACTIVE, "During trip (before 8:58 am), state should be updated to active."),
            Arguments.of(MONDAY_20200608_NOON, true, TRIP_ACTIVE, TRIP_UPCOMING, "After trip (after 9am), state should change to upcoming (for recurring trip)."),
            Arguments.of(MONDAY_20200608_NOON, true, NEXT_TRIP_NOT_POSSIBLE, TRIP_UPCOMING, "Stale trip status should be updated to upcoming (for recurring trip)."),
            Arguments.of(TUESDAY_20200609.withHour(10), false, NEXT_TRIP_NOT_POSSIBLE, PAST_TRIP, "Stale trip status should be updated to past (for one-time trip)."),
            Arguments.of(TUESDAY_20200609_0800, true, TRIP_ACTIVE, TRIP_UPCOMING, "Shortly before trip starts, state should change to upcoming."),
            Arguments.of(TUESDAY_20200609.withHour(4), true, TRIP_ACTIVE, TRIP_UPCOMING, "Long before trip starts, state should change to upcoming."),
            Arguments.of(TUESDAY_20200609.withHour(4), true, NO_LONGER_POSSIBLE, NO_LONGER_POSSIBLE, "Should not attempt to update a trip no longer possible.")
        );
    }

    @ParameterizedTest
    @MethodSource("shouldNotUpdateInactiveOrSnoozedTripCases")
    void shouldNotUpdateInactiveOrSnoozedTrip(
        ZonedDateTime clockTime,
        boolean isActive,
        boolean isSnoozed
    ) throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.id = UUID.randomUUID().toString();
        monitoredTrip.tuesday = true;
        monitoredTrip.journeyState = OtpTestUtils.createDefaultJourneyState();
        monitoredTrip.journeyState.targetDate = "2020-06-09";
        monitoredTrip.journeyState.tripStatus = TRIP_UPCOMING; // Not active, to create a state discrepancy.
        monitoredTrip.snoozed = isSnoozed;
        monitoredTrip.isActive = isActive;
        monitoredTrip.journeyState.lastCheckedEpochMillis =
            DateTimeUtils.nowAsZonedDateTime().minusDays(300).toInstant().toEpochMilli();

        Persistence.monitoredTrips.create(monitoredTrip);

        // Mock the current time
        DateTimeUtils.useFixedClockAt(clockTime);

        // After trip has completed, check that trip status has been updated.
        CheckMonitoredTrip check = tripChecker(monitoredTrip, firstItinerary(mockOtpPlanResponse()));
        check.run();

        MonitoredTrip modifiedTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        assertEquals(
            monitoredTrip.journeyState.lastCheckedEpochMillis,
            modifiedTrip.journeyState.lastCheckedEpochMillis,
            "Should not check trip if " + (isActive ? "" : "in") + "active and " + (isSnoozed ? "" : "not ") + "snoozed."
        );
    }

    private static Stream<Arguments> shouldNotUpdateInactiveOrSnoozedTripCases() {
        // (Trips for these tests start on Tuesday, June 9, 2020 at 8:40am and ends at 8:58am.)
        // The initial state for the trip is TRIP_ACTIVE.
        return Stream.of(
            Arguments.of(TUESDAY_20200609_0850, true, true),
            Arguments.of(TUESDAY_20200609_0850, false, true),
            Arguments.of(TUESDAY_20200609_0850, false, false)
        );
    }

    @Test
    void testDuplicateNotifications() throws Exception {
        OtpUser observer = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-observer-user"));

        MonitoredTrip monitoredTrip = monitoredTripWithLegId();
        monitoredTrip.primary = new MobilityProfileLite(user);
        monitoredTrip.observers.add(new RelatedUser(observer.email, RelatedUser.RelatedUserStatus.CONFIRMED));
        Persistence.monitoredTrips.create(monitoredTrip);

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
        CheckMonitoredTrip checkMonitoredTrip = tripChecker(monitoredTrip, firstItinerary(mockOtpPlanResponse()));
        checkMonitoredTrip.IS_TEST = true;
        checkMonitoredTrip.targetZonedDateTime = monitoredTrip.tripZonedDateTime(DateTimeUtils.nowAsLocalDate());
        checkMonitoredTrip.processLegTransition(NotificationType.MODE_CHANGE_NOTIFICATION, travelerPosition);
    }

    /**
     * Edge case when UTC time is 1 day ahead of local time. If this is failing, ensure OTP_TIMEZONE is set in env.yml
     */
    @Test
    void testCheckMonitoredTripWhenUTCIsNextDay() throws Exception {
        MonitoredTrip monitoredTrip = monitoredTripWithLegId();

        // monitored trip start time = 1:30AM UTC or 5:30PM PST
        ZonedDateTime itineraryDayAt0030 = DateTimeUtils.makeOtpZonedDateTime(monitoredTrip.itinerary.startTime).withHour(17).withMinute(30);
        OtpTestUtils.updateBaseItineraryTime(monitoredTrip.itinerary, itineraryDayAt0030);

        monitoredTrip.leadTimeInMinutes = 30;
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);

        // Set up an OTP mock response in order to trigger some of the monitor checks.
        Itinerary itinerary = firstItinerary(mockOtpPlanResponse());

        // itinerary start time = 1:30AM UTC or 5:30PM PST
        OtpTestUtils.updateBaseItineraryTime(itinerary, itineraryDayAt0030);

        // change "now" time after initial check to be within 30 min lead
        // 1:00AM UTC or 5:00PM PST
        DateTimeUtils.useFixedClockAt(itineraryDayAt0030.withMinute(0));

        CheckMonitoredTrip checkMonitoredTrip = tripChecker(monitoredTrip, itinerary);
        checkMonitoredTrip.run();

        // trip should not have been skipped
        Assertions.assertEquals(itineraryDayAt0030.getDayOfWeek(), checkMonitoredTrip.targetZonedDateTime.getDayOfWeek());

        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    private static CheckMonitoredTrip tripChecker(
        MonitoredTrip monitoredTrip,
        Itinerary mockItinerary,
        LocalDate mockDate
    ) throws CloneNotSupportedException {
        return new CheckMonitoredTrip(
            monitoredTrip,
            null,
            new LegFinder(
                new MockLegResponseProvider(
                    mockItinerary,
                    leg -> LegIdProcessor.computeLegIdForServiceDate(leg, mockDate)
                )::getLegResponse,
                LegIdProcessor::computeLegIdForServiceDate
            )
        );
    }

    private static CheckMonitoredTrip tripChecker(
        MonitoredTrip monitoredTrip,
        Itinerary mockItinerary
    ) throws CloneNotSupportedException {
        return tripChecker(monitoredTrip, mockItinerary, DateTimeUtils.nowAsLocalDate());
    }

    private static MonitoredTrip monitoredTripWithLegId() throws Exception {
        return PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE_LEGID,
            false,
            null
        );
    }

    /**
     * Supports delay notification tests.
     */
    static class DelayCase {
        public int departureDelay;
        public int arrivalDelay;
        public boolean isRealTime;
        public String message;
        public ZonedDateTime clockTime;
        public int expectedNotifications;

        public DelayCase(int depDelay, int arrDelay, boolean realTime, ZonedDateTime time, int notifications, String msg) {
            departureDelay = depDelay;
            arrivalDelay = arrDelay;
            isRealTime = realTime;
            clockTime = time;
            expectedNotifications = notifications;
            message = msg;
        }
    }
}
