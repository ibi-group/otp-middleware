package org.opentripplanner.middleware.otp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OtpRequestProcessorTest {

    @Test
    public void testStripFirstOtpPathPrefix() {
        // Below is an example for a typical OTP instance:
        // - The first otp is for the middleware's OTP proxy endpoint and should be removed.
        // - The second otp is part of the OTP server default router path and should be kept.
        // (The default router path is typically set in OTP-RR config as /otp/routers/default.)
        final String requestUri = "/otp/otp/routers/default/park_and_ride";

        String otpUri = OtpRequestProcessor.stripFirstOtpPathPrefix(requestUri);

        assertEquals("/otp/routers/default/park_and_ride", otpUri);
    }
}
