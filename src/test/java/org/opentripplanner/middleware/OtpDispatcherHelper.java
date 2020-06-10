package org.opentripplanner.middleware;

import org.opentripplanner.middleware.otp.OtpDispatcherImpl;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.utils.JsonUtils;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Used to provide real plan responses from OTP
 */
public class OtpDispatcherHelper {

    /**
     * Provides an actual plan response based on the fixed query plans
     */
    public static OtpDispatcherResponse getPlanFromOtp(String otpServer, String planEndpoint) {
        OtpDispatcherImpl otpDispatcher = new OtpDispatcherImpl(otpServer);
        OtpDispatcherResponse response = otpDispatcher.getPlan("plan?userId=123456&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=", planEndpoint);
        // convert plan response into concrete POJOs
        response.response = JsonUtils.getPOJOFromJSON(response.responseBody, Response.class);
        System.out.println("OTP Plan response:" + response.toString());
        return response;
    }

    /**
     * Provides an actual plan response error from OTP based on a misspelled parameter
     */
    public static OtpDispatcherResponse getPlanErrorFromOtp(String otpServer, String planEndpoint) {
        OtpDispatcherImpl otpDispatcher = new OtpDispatcherImpl(otpServer);
        // fromPalce instead of fromPlace to produce error
        OtpDispatcherResponse response = otpDispatcher.getPlan("plan?userId=123456&fromPalce=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=", planEndpoint);
        // convert plan response into concrete POJOs
        response.response = JsonUtils.getPOJOFromJSON(response.responseBody, Response.class);
        System.out.println("OTP Plan error response:" + response.toString());
        return response;
    }
}
