package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

/**
 * Plan response, place information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Place implements Cloneable {

    public String name;
    public Double lon;
    public Double lat;
    public Date departure;
    public String orig;
    public String vertexType;
    public String stopId;
    public Date arrival;
    public Integer stopIndex;
    public Integer stopSequence;
    public String stopCode;
    public String platformCode;
    public String zoneId;
    public String bikeShareId;
    public Set<String> networks;
    public String address;

    /**
     * This method calculates equality in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries are the same.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Place place = (Place) o;
        // FIXME account for slight stop repositioning by calculating equality based off of proximity to previous stop
        return lon.equals(place.lon) &&
            lat.equals(place.lat) &&
            Objects.equals(stopId, place.stopId) &&
            Objects.equals(vertexType, place.vertexType);
    }

    /**
     * This method calculates equality in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries are the same.
     */
    @Override
    public int hashCode() {
        // FIXME account for slight stop repositioning by calculating hashCode based off of proximity to previous stop
        return Objects.hash(lon, lat, stopId, vertexType);
    }

    protected Place clone() throws CloneNotSupportedException {
        return (Place) super.clone();
    }
}
