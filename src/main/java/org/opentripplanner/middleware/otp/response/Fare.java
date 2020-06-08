package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Plan response, fare information parent. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fare {

    @JsonProperty("regular")
    public Price regular;
    @JsonProperty("student")
    public Price student;
    @JsonProperty("senior")
    public Price senior;
    @JsonProperty("tram")
    public Price tram;
    @JsonProperty("special")
    public Price special;
    @JsonProperty("youth")
    public Price youth;

}
