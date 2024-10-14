package org.opentripplanner.middleware.otp.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** OTP 'plan' query variables */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryVariables {
    public boolean arriveBy;
    public RoutesAndTrips banned;
    public Float bikeReluctance;
    public Float carReluctance;
    public String date;
    public String fromPlace;
    public String mobilityProfile;
    public List<TransportMode> modes;
    public int numItineraries;
    public RoutesAndTrips preferred;
    public String time;
    public String toPlace;
    public RoutesAndTrips unpreferred;
    public Float walkReluctance;
    public Float walkSpeed;
    public boolean wheelchair;
}
