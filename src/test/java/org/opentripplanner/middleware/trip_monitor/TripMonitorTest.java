package org.opentripplanner.middleware.trip_monitor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;

public class TripMonitorTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(TripMonitorTest.class);
    private static OtpUser user;

    @BeforeAll
    public static void setup() {
        String email = "test@example.com";
        String phone = "+15551234";
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
    @Test @Disabled
    public void canMonitorTrip() {
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

    @Test @Disabled
    public void canSendEmailNotification() {
        NotificationUtils.sendEmail(user.email, "Hi there", "This is the body", null);
    }

    @Test @Disabled
    public void canSendSendGridEmailNotification() {
        boolean success = NotificationUtils.sendSendGridEmail(
            user.email,
            "Hi there",
            "This is the body",
            null
        );
        Assertions.assertTrue(success);
    }

    @Test @Disabled
    public void canSendSmsNotification() {
        // Note: toPhone must be verified.
        String messageId = NotificationUtils.sendSMS(
            user.phoneNumber,
            "This is the ship that made the Kessel Run in fourteen parsecs?"
        );
        Assertions.assertNotNull(messageId);
    }
}
