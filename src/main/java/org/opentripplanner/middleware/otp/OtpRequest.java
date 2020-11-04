package org.opentripplanner.middleware.otp;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Contains information needed to build a plan request to OpenTripPlanner.
 */
public class OtpRequest {
    public ZonedDateTime date;
    public Map<String, String> requestParameters;

    public OtpRequest(ZonedDateTime date, Map<String, String> requestParameters) {
        this.date = date;
        this.requestParameters = requestParameters;
    }
}
