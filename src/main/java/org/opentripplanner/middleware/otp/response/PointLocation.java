package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;

/**
 * Plan response, point location information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "lon",
    "lat",
    "departure",
    "orig",
    "vertexType",
    "stopId",
    "arrival",
    "stopIndex",
    "stopSequence"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointLocation {

    @JsonProperty("name")
    public String name;
    @JsonProperty("lon")
    public Double lon;
    @JsonProperty("lat")
    public Double lat;
    @JsonProperty("departure")
    public Date departure;
    @JsonProperty("orig")
    public String orig;
    @JsonProperty("vertexType")
    public String vertexType;
    @JsonProperty("stopId")
    public String stopId;
    @JsonProperty("arrival")
    public Date arrival;
    @JsonProperty("stopIndex")
    public Integer stopIndex;
    @JsonProperty("stopSequence")
    public Integer stopSequence;
}
