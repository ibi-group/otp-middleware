package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** OTP GraphQL data structure for preferred/unpreferred/banned routes and trips */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpGraphQLRoutesAndTrips implements Cloneable {
    public String routes;
    public String trips;

    @Override
    public OtpGraphQLRoutesAndTrips clone() {
        OtpGraphQLRoutesAndTrips clone = new OtpGraphQLRoutesAndTrips();
        clone.routes = routes;
        clone.trips = trips;
        return clone;
    }
}
