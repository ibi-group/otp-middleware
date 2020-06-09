package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan response, fare information parent. Produced using http://www.jsonschema2pojo.org/
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fare {

    // FIXME this may only ever return 'regular' making the other parameters redundant
    public Price regular;
    public Price student;
    public Price senior;
    public Price tram;
    public Price special;
    public Price youth;

}
