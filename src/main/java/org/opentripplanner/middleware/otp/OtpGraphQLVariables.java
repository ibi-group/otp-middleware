package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** OTP 'plan' query variables */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpGraphQLVariables {
    public boolean arriveBy;
    public OtpGraphQLRoutesAndTrips banned;
    public Float bikeReluctance;
    public Float carReluctance;
    public String date;
    public String fromPlace;
    public String mobilityProfile;
    public List<OtpGraphQLTransportMode> modes;
    public int numItineraries;
    public OtpGraphQLRoutesAndTrips preferred;
    public String time;
    public String toPlace;
    public OtpGraphQLRoutesAndTrips unpreferred;
    public Float walkReluctance;
    public Float walkSpeed;
    public boolean wheelchair;
}
