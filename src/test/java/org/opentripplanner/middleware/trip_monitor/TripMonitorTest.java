package org.opentripplanner.middleware.trip_monitor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.trip_monitor.jobs.CheckMonitoredTrip;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;

/**
 * This class contains tests for the {@link CheckMonitoredTrip} job and the {@link NotificationUtils} it uses.
 */
public class TripMonitorTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(TripMonitorTest.class);
    private static OtpUser user;

    @BeforeAll
    public static void setup() {
        // Note: In order to run the notification tests, these values must be provided in in system
        // environment variables, which can be defined in a run configuration in your IDE.
        String email = System.getenv("TEST_TO_EMAIL");
        // Phone must be in the form "+15551234" and must be verified first in order to send notifications
        String phone = System.getenv("TEST_TO_PHONE");
        user = createUser(email, phone);
    }

    @AfterAll
    public static void tearDown() {
        Persistence.otpUsers.removeById(user.id);
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
        new CheckMonitoredTrip(monitoredTrip, simulatedResponse).run();
        // Clear the created trip.
        Persistence.monitoredTrips.removeById(monitoredTrip.id);
    }

    @Test
    public void canSendSparkpostEmailNotification() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        boolean success = NotificationUtils.sendEmail(user.email, "Hi there", "This is the body", null);
        Assertions.assertTrue(success);
    }

    @Test
    public void canSendSendGridEmailNotification() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        boolean success = NotificationUtils.sendSendGridEmail(
            user.email,
            "Hi there",
            "This is the body",
            null
        );
        Assertions.assertTrue(success);
    }

    @Test
    public void canSendTwilioSmsNotification() {
        assumeTrue(getBooleanEnvVar("RUN_E2E"));
        // Note: toPhone must be verified.
        String messageId = NotificationUtils.sendSMS(
            // Note: phone number is configured in setup method above.
            user.phoneNumber,
            "This is the ship that made the Kessel Run in fourteen parsecs?"
        );
        LOG.info("Notification (id={}) successfully sent to {}", messageId, user.phoneNumber);
        Assertions.assertNotNull(messageId);
    }
}
