package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.ConvertsToCoordinates;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step implements ConvertsToCoordinates, Cloneable {

    public static final String DEPART = "DEPART";
    public static final String END_OF_ROUTING = "END_OF_ROUTING";

    /**
     * The distance in meters that this step takes.
     */
    public Double distance;

    /**
     * The relative direction (e.g. left or right turn) to take when engaging this step.
     */
    public String relativeDirection;

    /**
     * The name of the street, road, or path taken for this step.
     */
    public String streetName;

    /**
     * The cardinal (compass) direction (e.g. north, northeast) taken when engaging this step.
     */
    public String absoluteDirection;

    /**
     * The longitude of the start of the step.
     */
    public Double lon;

    /**
     * The latitude of the start of the step.
     */
    public Double lat;

    /**
     * Clone this object.
     * NOTE: This is used primarily during testing and only clones certain needed items so not all entities are
     * deep-cloned. Implement this further if additional items should be deep-cloned.
     */
    protected Step clone() throws CloneNotSupportedException {
        return (Step) super.clone();
    }

    public Coordinates toCoordinates() {
        return new Coordinates(lat, lon);
    }

    @JsonIgnore
    @BsonIgnore
    public boolean isEndOfRouting() {
        return END_OF_ROUTING.equals(relativeDirection);
    }
}
