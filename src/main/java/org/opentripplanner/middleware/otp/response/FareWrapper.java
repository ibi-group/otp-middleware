package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan response, fare information wrapper. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareWrapper {

    public Fare fare;
    public FareDetails details;
}
