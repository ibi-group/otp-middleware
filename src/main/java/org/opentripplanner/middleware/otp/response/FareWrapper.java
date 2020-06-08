package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.*;

/**
 * Plan response, fare information wrapper. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareWrapper {

    @JsonProperty("fare")
    public Fare fare;
    @JsonProperty("details")
    public FareDetails details;
}
