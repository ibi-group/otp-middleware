package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan response, step information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step {

    public Double distance;
    public String relativeDirection;
    public String streetName;
    public String absoluteDirection;
    public Boolean stayOn;
    public Boolean area;
    public Boolean bogusName;
    public Double lon;
    public Double lat;
}