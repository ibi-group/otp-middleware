package org.opentripplanner.middleware.otp;

/** OTP GraphQL data structure for preferred/unpreferred/banned routes and trips */
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
