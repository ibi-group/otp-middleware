package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Plan response, fare details. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareDetails {

    // FIXME this may only ever return 'regular' making the other parameters redundant
    public List<FareComponent> regular = null;
    public List<FareComponent> student = null;
    public List<FareComponent> senior = null;
    public List<FareComponent> tram = null;
    public List<FareComponent> special = null;
    public List<FareComponent> youth = null;

}