package org.opentripplanner.middleware.tripmonitor.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.TrackedJourney;
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
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static com.mongodb.client.model.Filters.eq;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.NEXT_TRIP_NOT_POSSIBLE;
import static org.opentripplanner.middleware.tripmonitor.TripStatus.TRIP_ACTIVE;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTripBasicTest.makeMonitoredTripFromNow;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTripBasicTest.setRecurringTodayAndTomorrow;

/**
 * This class contains tests for the {@link CheckMonitoredTrip} job.
 */
public class CheckMonitoredTripTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTripTest.class);
    private static OtpUser user;

    // this is initialized in the setup method after the OTP_TIMEZONE config value is known.
    private static final ZonedDateTime noonMonday8June2020 = DateTimeUtils.makeOtpZonedDateTime(new Date())
        .withYear(2020)
        .withMonth(6)
        .withDayOfMonth(8)
        .withHour(12)
        .withMinute(0);

    @BeforeAll
    public static void setup() {
        user = PersistenceTestUtils.createUser("user@example.com");
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

    /**
     * To run this trip, change the env.yml config values for OTP_API_ROOT
     * (and OTP_PLAN_ENDPOINT) to a valid OTP server.
     */
    @Test
    void canMonitorTrip() throws Exception {
        MonitoredTrip monitoredTrip = PersistenceTestUtils.createMonitoredTrip(
            user.id,
            OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.clone(),
            false,
            OtpTestUtils.createDefaultJourneyState()
        );
        monitoredTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);

        // Setup an OTP mock response in order to trigger some of the monitor checks.
        OtpResponse mockResponse = mockOtpPlanResponse();
        Itinerary mockMondayJune15Itinerary = mockResponse.plan.itineraries.get(0);

        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
        );

        // Add fake alerts to simulated itinerary.
        ArrayList<LocalizedAlert> fakeAlerts = new ArrayList<>();
        fakeAlerts.add(new LocalizedAlert());
        mockMondayJune15Itinerary.legs.get(1).alerts = fakeAlerts;

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

        // Next, run a monitor trip check from the new monitored trip using the simulated response.
        CheckMonitoredTrip checkMonitoredTrip = new CheckMonitoredTrip(monitoredTrip, () -> mockResponse);
        checkMonitoredTrip.run();
        // Assert that there is one notification generated during check.
        Assertions.assertEquals(1, checkMonitoredTrip.notifications.size());
        // Clear the created trip.
        PersistenceTestUtils.deleteMonitoredTrip(monitoredTrip);
    }

    @ParameterizedTest
    @MethodSource("createDelayNotificationTestCases")
    void testDelayNotifications(
        int minutesLate,
        int previousMinutesLate,
        String expectedDeparturePattern,
        String expectedArrivalPattern,
        String message
    ) throws Exception {
        long previousDelayMillis = TimeUnit.MILLISECONDS.convert(previousMinutesLate, TimeUnit.MINUTES);
        JourneyState journeyState = OtpTestUtils.createDefaultJourneyState();
        journeyState.baselineDepartureTimeEpochMillis += previousDelayMillis;
        journeyState.baselineArrivalTimeEpochMillis += previousDelayMillis;

        CheckMonitoredTrip check = createCheckMonitoredTrip(journeyState, this::mockOtpPlanResponse);
        check.matchingItinerary.offsetTimes(TimeUnit.MILLISECONDS.convert(minutesLate, TimeUnit.MINUTES));

        NotificationType[] notificationTypes = new NotificationType[] {
            NotificationType.DEPARTURE_DELAY,
            NotificationType.ARRIVAL_DELAY
        };
        String[] expectedPatterns = new String[] { expectedDeparturePattern, expectedArrivalPattern };
        for (int i = 0; i < notificationTypes.length; i++) {
            TripMonitorNotification notification = check.checkTripForDelay(notificationTypes[i]);
            String expectedNotificationPattern = expectedPatterns[i];
            if (expectedNotificationPattern == null) {
                assertNull(notification, message);
            } else {
                assertNotNull(
                    notification,
                    String.format("Expected %s notification for test case: %s", notificationTypes[i], message)
                );
                assertThat(message, notification.body, matchesPattern(expectedNotificationPattern));
            }
        }
    }

    private static Stream<Arguments> createDelayNotificationTestCases() {
        // These cases assume the default delay threshold of 15 minutes.

        // Note on patterns in the cases below:
        // JDK 20 uses narrow no-break space U+202F before "PM" for time format; earlier JDKs just use a space.
        return Stream.of(
            Arguments.of(0, 0, null, null, "On-time trip previously on-time => no delay notification"),
            // 20m late trip, prev. on-time => produce delay/arrival notifications
            Arguments.of(
                20,
                0,
                "⏱ Your trip is now predicted to depart 20 minutes late \\(at 9:00[\\u202f ]AM\\)\\.",
                "⏱ Your trip is now predicted to arrive 20 minutes late \\(at 9:18[\\u202f ]AM\\)\\.",
                "20m-late trip previously on-time => show delay notifications"
            ),
            Arguments.of(
                -18,
                0,
                "⏱ Your trip is now predicted to depart 18 minutes early \\(at 8:22[\\u202f ]AM\\)\\.",
                "⏱ Your trip is now predicted to arrive 18 minutes early \\(at 8:40[\\u202f ]AM\\)\\.",
                "18m-early trip previously on-time => show delay (early) notifications"
            ),
            Arguments.of(20, 15, null, null, "Trip previously 15m late, now 20m late => no notification"),
            Arguments.of(
                0,
                15,
                "⏱ Your trip is now predicted to depart about on time \\(at 8:40[\\u202f ]AM\\)\\.",
                "⏱ Your trip is now predicted to arrive about on time \\(at 8:58[\\u202f ]AM\\)\\.",
                "On-time trip previously late => show on-time notifications"
            )
        );
    }

    /**
     * Convenience method for creating a CheckMonitoredTrip instance with the default journey state.
     */
    private static CheckMonitoredTrip createCheckMonitoredTrip(Supplier<OtpResponse> otpResponseProvider) throws Exception {
        return createCheckMonitoredTrip(OtpTestUtils.createDefaultJourneyState(), otpResponseProvider);
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

        // - Return true for weekend trip when current time is on a weekday.
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
            noonMonday8June2020.withHour(9).withMinute(0), // mock time: 9:00am
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
    void canMakeOTPRequestAndUpdateMatchingItineraryForPreviouslyUnmatchedItinerary() throws Exception {
        // create an OTP mock to return
        OtpResponse mockWeekdayResponse = mockOtpPlanResponse();
        // create a mock monitored trip and CheckMonitorTrip instance.
        // Note that the response below gets modified from the original mockOtpPlanResponse.
        CheckMonitoredTrip mockCheckMonitoredTrip = createCheckMonitoredTrip(() -> mockWeekdayResponse);
        MonitoredTrip mockTrip = mockCheckMonitoredTrip.trip;
        Persistence.monitoredTrips.create(mockTrip);

        // create mock itinerary existence for trip
        mockTrip.itineraryExistence.monday = new ItineraryExistence.ItineraryExistenceResult();

        // update trip to say that itinerary was not possible on Mondays as of the last check
        mockTrip.itineraryExistence.monday.invalidDates.add("Mock date");

        // set trip status to be upcoming
        mockTrip.journeyState.tripStatus = TripStatus.TRIP_UPCOMING;

        // update the target date to be an upcoming Monday within the CheckMonitoredTrip
        mockCheckMonitoredTrip.targetZonedDateTime = noonMonday8June2020
            .withDayOfMonth(15)
            .withHour(8)
            .withMinute(35);

        Itinerary mockMondayJune15Itinerary = mockWeekdayResponse.plan.itineraries.get(0);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
        );

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

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
        mockCheckMonitoredTrip.targetZonedDateTime = noonMonday8June2020
            .withDayOfMonth(15)
            .withHour(8)
            .withMinute(35);

        Itinerary mockMondayJune15Itinerary = mockWeekdayResponse.plan.itineraries.get(0);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15, but at a time
        // that does not match the previous itinerary
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
                .withMinute(22) // this will cause an itinerary mismatch
        );

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

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
            "Your itinerary was not found in today's trip planner results. Please check real-time conditions and plan a new trip.",
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
        mockCheckMonitoredTrip.targetZonedDateTime = noonMonday8June2020
            .withDayOfMonth(15)
            .withHour(8)
            .withMinute(35);

        Itinerary mockMondayJune15Itinerary = mockWeekdayResponse.plan.itineraries.get(0);
        // parse original itinerary date/time and then update mock itinerary to occur on Monday June 15, but at a time
        // that does not match the previous itinerary
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
                .withMinute(22) // this will cause an itinerary mismatch
        );

        // mock the current time to be 8:45am on Monday, June 15
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

        // execute makeOTPRequestAndUpdateMatchingItinerary method and verify the expected outcome
        assertFalse(mockCheckMonitoredTrip.checkOtpAndUpdateTripStatus());

        // fetch updated trip from persistence
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(mockTrip.id);

        // verify that status is active
        assertEquals(
            TripStatus.NO_LONGER_POSSIBLE,
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

    @Test
    void shouldReportOneTimeTripInPastAsCompleted() throws CloneNotSupportedException {
        MonitoredTrip trip = makeMonitoredTripFromNow(-900, -300);
        trip.journeyState.matchingItinerary = trip.itinerary;
        trip.journeyState.tripStatus = TRIP_ACTIVE;

        new CheckMonitoredTrip(trip, this::mockOtpPlanResponse).checkOtpAndUpdateTripStatus();
        assertEquals(TripStatus.PAST_TRIP, trip.journeyState.tripStatus);
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
        check.shouldSkipMonitoredTripCheck(false);
        check.checkOtpAndUpdateTripStatus();
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

        OtpResponse expectedResponse = getMockOtpResponse();
        OtpResponse unexpectedResponse = getMockOtpResponse();
        // Remove the final itinerary leg so that the matching itineraries check fails.
        unexpectedResponse.plan.itineraries.get(0).legs.remove(2);

        // Mock the current time to be 8:45am on Monday, June 15, 2020.
        DateTimeUtils.useFixedClockAt(
            noonMonday8June2020
                .withDayOfMonth(15)
                .withHour(8)
                .withMinute(45)
        );

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
    private OtpResponse getMockOtpResponse() {
        OtpResponse mockResponse = mockOtpPlanResponse();
        Itinerary mockMondayJune15Itinerary = mockResponse.plan.itineraries.get(0);
        OtpTestUtils.updateBaseItineraryTime(
            mockMondayJune15Itinerary,
            DateTimeUtils.makeOtpZonedDateTime(mockMondayJune15Itinerary.startTime)
                .withDayOfMonth(15)
        );
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
}
