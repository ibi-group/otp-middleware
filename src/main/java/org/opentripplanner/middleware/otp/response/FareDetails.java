package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Plan response, fare details. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareDetails {

    @JsonProperty("regular")
    public List<FareComponent> regular = null;
    @JsonProperty("student")
    public List<FareComponent> student = null;
    @JsonProperty("senior")
    public List<FareComponent> senior = null;
    @JsonProperty("tram")
    public List<FareComponent> tram = null;
    @JsonProperty("special")
    public List<FareComponent> special = null;
    @JsonProperty("youth")
    public List<FareComponent> youth = null;

}