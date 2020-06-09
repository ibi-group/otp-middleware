package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.List;

/**
 * Plan response, itinerary leg information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leg {

    public Date startTime;
    public Date endTime;
    public Integer departureDelay;
    public Integer arrivalDelay;
    public Boolean realTime;
    public Double distance;
    public Boolean pathway;
    public String mode;
    public String route;
    public Boolean interlineWithPreviousLeg;
    public Place from;
    public Place to;
    public EncodedPolyline legGeometry;
    public Boolean rentedBike;
    public Boolean rentedCar;
    public Boolean rentedVehicle;
    public Boolean hailedCar;
    public Boolean transitLeg;
    public Double duration;
    public List<Place> intermediateStops = null;
    public List<Step> steps = null;
    public String agencyName;
    public String agencyUrl;
    public Integer routeType;
    public String routeId;
    public String agencyId;
    public String tripId;
    public String serviceDate;
    public List<EncodedPolyline> interStopGeometry = null;
    public String routeShortName;
    public String routeLongName;
}
