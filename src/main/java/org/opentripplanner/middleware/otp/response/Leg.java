package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;
import java.util.List;

/**
 * Plan response, itinerary leg information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "startTime",
    "endTime",
    "departureDelay",
    "arrivalDelay",
    "realTime",
    "distance",
    "pathway",
    "mode",
    "route",
    "agencyTimeZoneOffset",
    "interlineWithPreviousLeg",
    "from",
    "to",
    "legGeometry",
    "rentedBike",
    "rentedCar",
    "rentedVehicle",
    "hailedCar",
    "transitLeg",
    "duration",
    "intermediateStops",
    "steps",
    "agencyName",
    "agencyUrl",
    "routeType",
    "routeId",
    "agencyId",
    "tripId",
    "serviceDate",
    "interStopGeometry",
    "routeShortName",
    "routeLongName"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leg {

    @JsonProperty("startTime")
    public Date startTime;
    @JsonProperty("endTime")
    public Date endTime;
    @JsonProperty("departureDelay")
    public Integer departureDelay;
    @JsonProperty("arrivalDelay")
    public Integer arrivalDelay;
    @JsonProperty("realTime")
    public Boolean realTime;
    @JsonProperty("distance")
    public Double distance;
    @JsonProperty("pathway")
    public Boolean pathway;
    @JsonProperty("mode")
    public String mode;
    @JsonProperty("route")
    public String route;
    @JsonProperty("interlineWithPreviousLeg")
    public Boolean interlineWithPreviousLeg;
    @JsonProperty("from")
    public PointLocation from;
    @JsonProperty("to")
    public PointLocation to;
    @JsonProperty("legGeometry")
    public LegGeometry legGeometry;
    @JsonProperty("rentedBike")
    public Boolean rentedBike;
    @JsonProperty("rentedCar")
    public Boolean rentedCar;
    @JsonProperty("rentedVehicle")
    public Boolean rentedVehicle;
    @JsonProperty("hailedCar")
    public Boolean hailedCar;
    @JsonProperty("transitLeg")
    public Boolean transitLeg;
    @JsonProperty("duration")
    public Double duration;
    @JsonProperty("intermediateStops")
    public List<PointLocation> intermediateStops = null;
    @JsonProperty("steps")
    public List<Step> steps = null;
    @JsonProperty("agencyName")
    public String agencyName;
    @JsonProperty("agencyUrl")
    public String agencyUrl;
    @JsonProperty("routeType")
    public Integer routeType;
    @JsonProperty("routeId")
    public String routeId;
    @JsonProperty("agencyId")
    public String agencyId;
    @JsonProperty("tripId")
    public String tripId;
    @JsonProperty("serviceDate")
    public String serviceDate;
    @JsonProperty("interStopGeometry")
    public List<InterStopGeometry> interStopGeometry = null;
    @JsonProperty("routeShortName")
    public String routeShortName;
    @JsonProperty("routeLongName")
    public String routeLongName;
}
