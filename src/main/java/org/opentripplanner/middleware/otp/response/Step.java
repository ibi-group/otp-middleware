package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Plan response, step information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step {

    @JsonProperty("distance")
    public Double distance;
    @JsonProperty("relativeDirection")
    public String relativeDirection;
    @JsonProperty("streetName")
    public String streetName;
    @JsonProperty("absoluteDirection")
    public String absoluteDirection;
    @JsonProperty("stayOn")
    public Boolean stayOn;
    @JsonProperty("area")
    public Boolean area;
    @JsonProperty("bogusName")
    public Boolean bogusName;
    @JsonProperty("lon")
    public Double lon;
    @JsonProperty("lat")
    public Double lat;
}
