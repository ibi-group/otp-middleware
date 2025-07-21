package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncodedPolyline implements Cloneable {

    /**
     * The number of points in the string.
     */
    public Integer length;

    /**
     * List of coordinates of in a Google encoded polyline format (see
     * https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
     */
    public String points;

    /**
     * Clone this object.
     * NOTE: This is used primarily during testing and only clones certain needed items so not all entities are
     * deep-cloned. Implement this further if additional items should be deep-cloned.
     */
    protected EncodedPolyline clone() throws CloneNotSupportedException {
        return (EncodedPolyline) super.clone();
    }
}
