package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Plan response, leg geometry information. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "points",
    "length"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegGeometry {

    @JsonProperty("points")
    public String points;
    @JsonProperty("length")
    public Integer length;
}
