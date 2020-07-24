package org.opentripplanner.middleware.trip_monitor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TripMonitorTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(TripMonitorTest.class);
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
        Persistence.monitoredTrips.create(monitoredTrip);
        LOG.info("Created trip {}", monitoredTrip.id);
        // Next, run a monitor trip check from the new monitored trip.
        new CheckMonitoredTrip(monitoredTrip).run();
        // Clear the created trip.
        Persistence.monitoredTrips.removeById(monitoredTrip.id);
    }

    @Test @Disabled
    public void canSendNotification() {
        String email = "you@email.com";
        NotificationUtils.sendNotification(email, "Hi there", "This is the body", null);
    }
}
