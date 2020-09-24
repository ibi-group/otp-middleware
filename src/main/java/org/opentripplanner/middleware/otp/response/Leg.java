package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Plan response, itinerary leg information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leg implements Cloneable {

    public Date startTime;
    public Date endTime;
    public Integer departureDelay;
    public Integer arrivalDelay;
    public Boolean realTime;
    public Double distance;
    public Boolean pathway;
    public String mode;
    public String route;
    public Boolean interlineWithPreviousLeg;
    public Place from;
    public Place to;
    public EncodedPolyline legGeometry;
    public Boolean rentedBike;
    public Boolean rentedCar;
    public Boolean rentedVehicle;
    public Boolean hailedCar;
    public Boolean transitLeg;
    public Double duration;
    public List<Place> intermediateStops = null;
    public List<Step> steps = null;
    public String agencyName;
    public String agencyUrl;
    public Integer routeType;
    public String routeId;
    public String agencyId;
    public String tripId;
    public String serviceDate;
    public List<EncodedPolyline> interStopGeometry = null;
    public String routeShortName;
    public String routeLongName;
    public List<LocalizedAlert> alerts = null;

    /**
     * This method calculates equality in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries are the same.
     *
     * FIXME: maybe don't check duration exactly as it might vary slightly in certain trips
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Leg leg = (Leg) o;
        return duration.equals(leg.duration) &&
            mode.equals(leg.mode) &&
            from.equals(leg.from) &&
            to.equals(leg.to) &&
            Objects.equals(rentedBike, leg.rentedBike) &&
            Objects.equals(rentedCar, leg.rentedCar) &&
            Objects.equals(rentedVehicle, leg.rentedVehicle) &&
            Objects.equals(hailedCar, leg.hailedCar) &&
            Objects.equals(transitLeg, leg.transitLeg) &&
            Objects.equals(routeType, leg.routeType) &&
            Objects.equals(route, leg.route);
        // also include headsign?
    }

    /**
     * This method calculates the hash code in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries match.
     *
     * FIXME: maybe don't check duration exactly as it might vary slightly in certain trips
     */
    @Override
    public int hashCode() {
        return Objects.hash(
            duration,
            mode,
            from,
            to,
            rentedBike,
            rentedCar,
            rentedVehicle,
            hailedCar,
            transitLeg,
            routeType,
            route
            // also include headsign?
        );
    }

    @Override
    protected Leg clone() throws CloneNotSupportedException {
        Leg cloned = (Leg) super.clone();
        cloned.from = this.from.clone();
        cloned.to = this.to.clone();
        cloned.steps = new ArrayList<>();
        for (Step step : this.steps) {
            cloned.steps.add(step.clone());
        }
        cloned.legGeometry = this.legGeometry.clone();
        return cloned;
    }
}
