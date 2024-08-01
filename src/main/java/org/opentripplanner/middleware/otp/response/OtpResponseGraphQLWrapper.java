package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a trip planner response
 *
 * This is a pared-down version of the org.opentripplanner.api.resource.Response class in OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpResponseGraphQLWrapper {
    public OtpResponse data;
}