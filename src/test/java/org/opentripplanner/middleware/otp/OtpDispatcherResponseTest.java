package org.opentripplanner.middleware.otp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.FileUtils;

import java.io.IOException;

import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;

public class OtpDispatcherResponseTest {
    @Test
    public void toStringShouldExcludeResponseFieldIfNotCallingPlan() throws IOException {
        String mockParkRideResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "otp/parkRideResponse.json"
        );


        // The call below creates a request URI that is "http://test.com",
        // which is not the OTP /plan endpoint, but we still assert that too.
        OtpDispatcherResponse dispatcherResponse = new OtpDispatcherResponse(mockParkRideResponse);
        Assertions.assertFalse(dispatcherResponse.requestUri.getPath().endsWith(OTP_PLAN_ENDPOINT));

        // Expected string does not include the response={} field.
        String expected = "OtpDispatcherResponse{statusCode=200, responseBody='" + mockParkRideResponse + "'}";

        Assertions.assertEquals(expected, dispatcherResponse.toString());
    }
}
