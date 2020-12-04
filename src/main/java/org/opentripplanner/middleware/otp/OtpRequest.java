package org.opentripplanner.middleware.otp;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Contains information needed to build a plan request to OpenTripPlanner.
 */
public class OtpRequest {
    public final ZonedDateTime dateTime;
    public final Map<String, String> requestParameters;

    public OtpRequest(ZonedDateTime dateTime, Map<String, String> requestParameters) {
        this.dateTime = dateTime;
        this.requestParameters = requestParameters;
    }
}
