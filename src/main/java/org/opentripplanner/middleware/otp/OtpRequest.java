package org.opentripplanner.middleware.otp;

import java.time.ZonedDateTime;

/**
 * Contains information needed to build a plan request to OpenTripPlanner.
 */
public class OtpRequest {
    public final ZonedDateTime dateTime;
    public final OtpGraphQLVariables requestParameters;

    public OtpRequest(ZonedDateTime dateTime, OtpGraphQLVariables requestParameters) {
        this.dateTime = dateTime;
        this.requestParameters = requestParameters;
    }
}
