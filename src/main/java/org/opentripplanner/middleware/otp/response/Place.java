package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.ConvertsToCoordinates;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Place implements ConvertsToCoordinates, Cloneable {

    /**
     * For transit stops, the name of the stop. For points of interest, the name of the POI.
     */
    public String name;

    /**
     * Longitude of the place.
     */
    public Double lon;

    /**
     * Latitude of the place.
     */
    public Double lat;

    /**
     * The stop related to the place.
     */
    public Stop stop;

    /**
     * Clone this object.
     * NOTE: This is used primarily during testing and only clones certain needed items so not all entities are
     * deep-cloned. Implement this further if additional items should be deep-cloned.
     */
    protected Place clone() throws CloneNotSupportedException {
        return (Place) super.clone();
    }

    public Coordinates toCoordinates() {
        return new Coordinates(lat, lon);
    }
}
