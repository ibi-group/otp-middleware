package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Plan response, step information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step implements Cloneable {

    public Double distance;
    public String relativeDirection;
    public String streetName;
    public String absoluteDirection;
    public Boolean stayOn;
    public Boolean area;
    public Boolean bogusName;
    public Double lon;
    public Double lat;

    /**
     * This method calculates equality in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries are the same.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Step step = (Step) o;
        return Objects.equals(streetName, step.streetName) &&
            // FIXME account for slight step repositioning by calculating equality based off of proximity to previous
            //   step
            lon.equals(step.lon) && lat.equals(step.lat);
    }

    /**
     * This method calculates equality in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries are the same.
     */
    @Override
    public int hashCode() {
        // FIXME account for slight step repositioning by calculating equality based off of proximity to previous step
        return Objects.hash(streetName, lon, lat);
    }

    protected Step clone() throws CloneNotSupportedException {
        return (Step) super.clone();
    }
}
