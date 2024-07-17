package org.opentripplanner.middleware.otp;

import java.util.List;

/** OTP 'plan' query variables */
public class OtpGraphQLVariables {
    public boolean arriveBy;
    public OtpGraphQLRoutesAndTrips banned;
    public float bikeReluctance;
    public float carReluctance;
    public String date;
    public String fromPlace;
    public List<OtpGraphQLTransportMode> modes;
    public int numItineraries;
    public OtpGraphQLRoutesAndTrips preferred;
    public String time;
    public String toPlace;
    public OtpGraphQLRoutesAndTrips unpreferred;
    public float walkReluctance;
    public float walkSpeed;
    public boolean wheelchair;
}
