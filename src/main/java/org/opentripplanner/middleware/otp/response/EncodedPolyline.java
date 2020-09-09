package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan response, encoded polyline information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncodedPolyline implements Cloneable {
    public String points;
    public String levels;
    public Integer length;

    protected EncodedPolyline clone() throws CloneNotSupportedException {
        return (EncodedPolyline) super.clone();
    }
}
