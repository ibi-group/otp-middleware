package org.opentripplanner.middleware.trip_monitor.jobs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;
import static org.opentripplanner.middleware.trip_monitor.jobs.CheckMonitoredTrip.shouldSkipMonitoredTripCheck;

/**
 * This class contains tests for the {@link CheckMonitoredTrip} job.
 */
public class CheckMonitoredTripTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(CheckMonitoredTripTest.class);
    private static OtpUser user;
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
        Persistence.monitoredTrips.removeFiltered(eq("userId", user.id));
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
    @Disabled // TODO enable once createSkipTripTestCases has at least one test case
    void testSkipMonitoredTripCheck(ShouldSkipTripTestCase testCase) {
        assertEquals(testCase.shouldSkipTrip, shouldSkipMonitoredTripCheck(testCase.trip), testCase.message);
    }

    private static List<ShouldSkipTripTestCase> createSkipTripTestCases() {
        List<ShouldSkipTripTestCase> testCases = new ArrayList<>();

        // - Returns false for weekend trip when current time is on a weekday.
        MonitoredTrip weekendTrip = createMonitoredTrip(user.id, otpDispatcherResponse);
        // - Returns false for weekday trip when current time is on a weekend.
        // - Returns true if trip is starting in greater than 1 hr, but the last time checked was 2 hours ago
        // - Returns false if trip is starting in greater than 1 hr, but the last time checked was 2 minutes ago
        // - Returns false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        // - Returns true if trip is starting in 45 minutes and the last time checked was 2 minutes ago
        // - Returns false if trip is starting in 45 minutes and the last time checked was 20 minutes ago
        // - Returns false if trip is starting in 10 minutes and the last time checked was 2 minutes ago
        // - Returns true if trip has ended 3 minutes ago

        return testCases;
    }

    private static class ShouldSkipTripTestCase {
        /* a helpful message describing the particular test case */
        public String message;
        /**
         * if true, it is expected that the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method should
         * calculate that the given trip should be skipped.
         */
        public boolean shouldSkipTrip;
        /**
         * The trip for the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method to calculate whether
         * skipping trip analysis should occur.
         */
        public MonitoredTrip trip;
    }
}
