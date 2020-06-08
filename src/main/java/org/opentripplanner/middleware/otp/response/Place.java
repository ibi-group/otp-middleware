package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;
import java.util.Set;

/**
 * Plan response, place information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Place {

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
    @JsonProperty("stopCode")
    public String stopCode;
    @JsonProperty("platformCode")
    public String platformCode = null;
    @JsonProperty("zoneId")
    public String zoneId;
    @JsonProperty("bikeShareId")
    public String bikeShareId;
    @JsonProperty("networks")
    public Set<String> networks;
    @JsonProperty("address")
    public String address;



}
