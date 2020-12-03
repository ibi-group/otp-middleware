package org.opentripplanner.middleware.otp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.testUtils.CommonTestUtils;

import java.io.IOException;
import java.net.URI;

import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;

public class OtpDispatcherResponseTest {

    @Test
    public void toStringShouldExcludeResponseFieldIfNotCallingPlan() throws IOException {
        String mockParkRideResponse = CommonTestUtils.getResourceFileContentsAsString(
            "otp/response/parkRideResponse.json"
        );


        // The call below creates a request URI that is "http://test.com",
        // which is not the OTP /plan endpoint, but we still assert that too.
        OtpDispatcherResponse dispatcherResponse = new OtpDispatcherResponse(
            mockParkRideResponse,
            URI.create("http://test.com/otp/routers/default/park_and_ride?maxTransitDistance=1000")
        );
        Assertions.assertFalse(dispatcherResponse.requestUri.getPath().endsWith(OTP_PLAN_ENDPOINT));

        // Expected string does not include the response={} field.
        String expected = "OtpDispatcherResponse{statusCode=200, responseBody='" + mockParkRideResponse + "'}";

        Assertions.assertEquals(expected, dispatcherResponse.toString());
    }
}
