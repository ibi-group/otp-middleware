package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.otp.OtpDispatcherImpl;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OtpDispatcherTest extends OtpMiddlewareTest {

    @Test
    public void canGetPlanFromOtp() {
        OtpDispatcherImpl otpDispatcher = new OtpDispatcherImpl("https://fdot-otp-server.ibi-transit.com");
        OtpDispatcherResponse response = otpDispatcher.getPlan("plan?userId=123456&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=");
        System.out.println("OTP Plan response:" + response);
        assertNotNull(response);
    }

//    http://localhost:4567/plan?userId=123456&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=
}
