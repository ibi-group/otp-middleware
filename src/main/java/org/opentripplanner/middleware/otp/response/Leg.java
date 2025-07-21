package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leg implements Cloneable {

    // TODO: Deprecated and replaced with 'start.estimated.time', but this introduces significant changes.
    /**
     * The date and time when this leg begins. Format: Unix timestamp in milliseconds.
     */
    public Date startTime;

    // TODO: Deprecated and replaced with 'end.estimated.time', but this introduces significant changes.
    /**
     * The date and time when this leg ends. Format: Unix timestamp in milliseconds.
     */
    public Date endTime;

    // TODO: Deprecated and replaced with 'end.estimated.delay', but this introduces significant changes.
    /**
     * For transit leg, the offset from the scheduled departure time of the boarding stop in this leg.
     */
    public Integer departureDelay;

    // TODO: Deprecated and replaced with 'start.estimated.delay', but this introduces significant changes.
    /**
     * For transit leg, the offset from the scheduled arrival time of the alighting stop in this leg.
     */
    public Integer arrivalDelay;

    /**
     * Whether there is real-time data about this Leg.
     */
    public Boolean realTime;

    /**
     * The distance traveled while traversing the leg in meters.
     */
    public Double distance;

    /**
     * The mode (e.g. WALK) used when traversing this leg.
     */
    public String mode;

    /**
     * Interlines with previous leg.
     * This is true when the same vehicle is used for the previous leg as for this leg
     * and passenger can stay inside the vehicle.
     */
    public Boolean interlineWithPreviousLeg;

    /**
     * The Place where the leg originates.
     */
    public Place from;

    /**
     * The Place where the leg ends.
     */
    public Place to;

    /**
     * The leg's geometry.
     */
    public EncodedPolyline legGeometry;

    /**
     * Whether this leg is traversed with a rented bike.
     */
    public Boolean rentedBike;

    /**
     * Estimate of a hailed ride like Uber.
     */
    public RideHailingEstimate rideHailingEstimate;

    /**
     * Whether this leg is a transit leg or not.
     */
    public Boolean transitLeg;

    /**
     * The leg's duration in seconds.
     */
    public Double duration;

    /**
     * For transit legs, intermediate stops between the Place where the leg
     * originates and the Place where the leg ends. For non-transit legs, null.
     */
    public List<Stop> intermediateStops = null;

    /**
     * The turn-by-turn navigation instructions.
     */
    public List<Step> steps = null;

    /**
     * Applicable alerts for this leg.
     */
    public List<Alert> alerts = null;

    /**
     * For transit legs, the headsign that the vehicle shows at the stop where the passenger boards.
     * For non-transit legs, null.
     */
    public String headsign;

    /**
     * For transit legs, the transit agency that operates the service used for this leg. For non-transit legs, null.
     */
    public Agency agency;

    /**
     * For transit legs, the route that is used for traversing the leg. For non-transit legs, null.
     */
    public Route route;

    /**
     * For transit legs, the trip that is used for traversing the leg. For non-transit legs, null.
     */
    public Trip trip;

    /**
     * Gets the scheduled start time of this itinerary in the OTP timezone.
     */
    @JsonIgnore
    @BsonIgnore
    public ZonedDateTime getScheduledStartTime() {
        return ZonedDateTime.ofInstant(
            startTime.toInstant().minusSeconds(departureDelay),
            DateTimeUtils.getOtpZoneId()
        );
    }

    /**
     * Gets the scheduled end time of this itinerary in the OTP timezone.
     */
    @JsonIgnore
    @BsonIgnore
    public ZonedDateTime getScheduledEndTime() {
        return ZonedDateTime.ofInstant(
            endTime.toInstant().minusSeconds(arrivalDelay),
            DateTimeUtils.getOtpZoneId()
        );
    }

    /**
     * Clone this object.
     * NOTE: This is used primarily during testing and only clones certain needed items so not all entities are
     * deep-cloned. Implement this further if additional items should be deep-cloned.
     */
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
