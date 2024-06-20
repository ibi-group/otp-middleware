package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.opentripplanner.middleware.utils.Coordinates;

/**
 * Plan response, step information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step extends Coordinates implements Cloneable {

    public Double distance;
    public String relativeDirection;
    public String streetName;
    public String absoluteDirection;
    public Boolean stayOn;
    public Boolean area;
    public Boolean bogusName;

    /**
     * Clone this object.
     * NOTE: This is used primarily during testing and only clones certain needed items so not all entities are
     * deep-cloned. Implement this further if additional items should be deep-cloned.
     */
    protected Step clone() throws CloneNotSupportedException {
        return (Step) super.clone();
    }
}
