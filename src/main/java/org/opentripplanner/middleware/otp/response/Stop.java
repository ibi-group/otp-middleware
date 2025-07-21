package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.opentripplanner.middleware.utils.ConvertsToCoordinates;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stop implements ConvertsToCoordinates, Cloneable {

    /**
     * Stop code which is visible at the stop.
     */
    public String code;

    /**
     * ÌD of the stop in format FeedId:StopId.
     */
    public String gtfsId;

    /**
     * Global object ID provided by Relay. This value can be used to re-fetch this object using node query.
     */
    public String id;

    /**
     * Longitude of the stop (WGS 84).
     */
    public Double lon;

    /**
     * Latitude of the stop (WGS 84).
     */
    public Double lat;

    public Stop() {
    }

    public Stop(Place place) {
        this.id = place.stop.id;
        this.lon = place.lon;
        this.lat = place.lat;
    }

    public Coordinates toCoordinates() {
        return new Coordinates(lat, lon);
    }

    /**
     * Clone this object.
     */
    protected Place clone() throws CloneNotSupportedException {
        return (Place) super.clone();
    }

}
