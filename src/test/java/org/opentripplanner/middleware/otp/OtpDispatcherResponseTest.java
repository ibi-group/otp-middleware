package org.opentripplanner.middleware.otp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.FileUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.TestUtils.TEST_RESOURCE_PATH;
import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_PLAN_ENDPOINT;

public class OtpDispatcherResponseTest {
    public static final URI DEFAULT_PLAN_URI = URI.create(
        String.format(
            "http://test.com/otp/routers/default/plan?%s",
            URLEncoder.encode(
                "date=2020-06-09&mode=WALK,BUS,TRAM,RAIL,GONDOLA&arriveBy=false&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=NaN&showIntermediateStops=true&optimize=QUICK&fromPlace=1709 NW Irving St, Portland 97209::45.527817334203,-122.68865964147231&toPlace=Uncharted Realities, SW 3rd Ave, Downtown - Portland 97204::45.51639151281627,-122.67681483620306&time=08:35&maxWalkDistance=1207",
                UTF_8
            )
        )
    );

    @Test
    public void toStringShouldExcludeResponseFieldIfNotCallingPlan() throws IOException {
        String mockParkRideResponse = FileUtils.getFileContents(
            TEST_RESOURCE_PATH + "otp/parkRideResponse.json"
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
