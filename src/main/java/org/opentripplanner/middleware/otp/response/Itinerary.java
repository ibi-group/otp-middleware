package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opentripplanner.middleware.utils.InvalidItineraryReason;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;

/**
 * An Itinerary is one complete way of getting from the start location to the end location.
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Itinerary implements Cloneable {

    /**
     * Duration of the trip on this itinerary, in seconds.
     */
    public Long duration = 0L;

    /**
     * Time that the trip departs.
     */
    public Date startTime = null;
    /**
     * Time that the trip arrives.
     */
    public Date endTime = null;

    /**
     * How much time is spent walking, in seconds.
     */
    public long walkTime = 0;
    /**
     * How much time is spent on transit, in seconds.
     */
    public long transitTime = 0;
    /**
     * How much time is spent waiting for transit to arrive, in seconds.
     */
    public long waitingTime = 0;

    /**
     * How far the user has to walk, in meters.
     */
    public Double walkDistance = 0.0;

    /**
     * Indicates that the walk limit distance has been exceeded for this itinerary when true.
     */
    public boolean walkLimitExceeded = false;

    /**
     * How much elevation is lost, in total, over the course of the trip, in meters. As an example,
     * a trip that went from the top of Mount Everest straight down to sea level, then back up K2,
     * then back down again would have an elevationLost of Everest + K2.
     */
    public Double elevationLost = 0.0;
    /**
     * How much elevation is gained, in total, over the course of the trip, in meters. See
     * elevationLost.
     */
    public Double elevationGained = 0.0;

    /**
     * The number of transfers this trip has.
     */
    public Integer transfers = 0;

    /**
     * Fare information for this itinerary.
     */
    public FareWrapper fare;

    /**
     * Leg information for this itinerary.
     */
    public List<Leg> legs = null;

    /**
     * @return set of reasons for why the itinerary cannot be monitored.
     */
    public Set<InvalidItineraryReason> checkItineraryCanBeMonitored() {
        // Check the itinerary for various conditions needed for monitoring.
        Set<InvalidItineraryReason> reasons = new HashSet<>();
        if (!hasTransit()) reasons.add(InvalidItineraryReason.MISSING_TRANSIT);
        if (hasRentalOrRideHail()) reasons.add(InvalidItineraryReason.HAS_RENTAL_OR_RIDE_HAIL);
        // TODO: Add additional checks here.
        return reasons;
    }

    /**
     * @return true if the itinerary can be monitored.
     */
    public boolean canBeMonitored() {
        return checkItineraryCanBeMonitored().isEmpty();
    }

    /**
     * Determines whether the itinerary includes transit.
     * @return true if at least one {@link Leg} of the itinerary is a transit leg per OTP.
     */
    public boolean hasTransit() {
        if (legs != null) {
            for (Leg leg : legs) {
                if (leg.transitLeg != null && leg.transitLeg) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines whether the itinerary includes a rental or ride hail.
     * @return true if at least one {@link Leg} of the itinerary is a rental or ride hail leg per OTP.
     */
    public boolean hasRentalOrRideHail() {
        if (legs != null) {
            for (Leg leg : legs) {
                if (TRUE.equals(leg.rentedBike) ||
                    TRUE.equals(leg.rentedCar) ||
                    TRUE.equals(leg.rentedVehicle) ||
                    TRUE.equals(leg.hailedCar)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * OTP-middleware specific function to aid in collecting alerts from legs.
     */
    public List<LocalizedAlert> getAlerts() {
        if (legs == null) return Collections.emptyList();
        return legs.stream()
            .map(leg -> leg.alerts)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    public void clearAlerts() {
        for (Leg leg : legs) {
            leg.alerts = null;
        }
    }

    /**
     * Get trip time as {@link ZonedDateTime} of itinerary (use of start/end depends on arriveBy).
     */
    @JsonIgnore
    @BsonIgnore
    public ZonedDateTime getTripTime(boolean arriveBy) {
        return ZonedDateTime.ofInstant(
            (arriveBy ? endTime : startTime).toInstant(),
            DateTimeUtils.getOtpZoneId()
        );
    }

    @Override
    public String toString() {
        return "Itinerary{" +
            "duration=" + duration +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            ", walkTime=" + walkTime +
            ", transitTime=" + transitTime +
            ", waitingTime=" + waitingTime +
            ", walkDistance=" + walkDistance +
            ", walkLimitExceeded=" + walkLimitExceeded +
            ", elevationLost=" + elevationLost +
            ", elevationGained=" + elevationGained +
            ", transfers=" + transfers +
            ", fare=" + fare +
            ", legs=" + legs +
            '}';
    }

    /**
     * Clone this object.
     * NOTE: This is used primarily during testing and only clones certain needed items so not all entities are
     * deep-cloned. Implement this further if additional items should be deep-cloned.
     */
    @Override
    public Itinerary clone() throws CloneNotSupportedException {
        Itinerary cloned = (Itinerary) super.clone();
        cloned.legs = new ArrayList<>();
        for (Leg leg : legs) {
            cloned.legs.add(leg.clone());
        }
        return cloned;
    }

    /**
     * Returns the scheduled start time of the itinerary in epoch milliseconds by subtracting any delay found in the
     * first transit leg if a transit leg exists.
     */
    @JsonIgnore
    @BsonIgnore
    public long getScheduledStartTimeEpochMillis() {
        long startTimeEpochMillis = startTime.getTime();
        for (Leg leg : legs) {
            if (leg.transitLeg) {
                startTimeEpochMillis -= TimeUnit.MILLISECONDS.convert(
                    leg.departureDelay,
                    TimeUnit.SECONDS
                );
                break;
            }
        }
        return startTimeEpochMillis;
    }

    /**
     * Returns the scheduled end time of the itinerary in epoch milliseconds by subtracting any delay found in the
     * last transit leg if a transit leg exists.
     */
    @JsonIgnore
    @BsonIgnore
    public long getScheduledEndTimeEpochMillis() {
        long endTimeEpochMillis = endTime.getTime();
        for (int i = legs.size() - 1; i >= 0; i--) {
            Leg leg = legs.get(i);
            if (leg.transitLeg) {
                endTimeEpochMillis -= TimeUnit.MILLISECONDS.convert(
                    leg.arrivalDelay,
                    TimeUnit.SECONDS
                );
                break;
            }
        }
        return endTimeEpochMillis;
    }

    /**
     * Returns true if the current time falls between the start and end time of the itinerary
     */
    public boolean isActive() {
        Date now = DateTimeUtils.nowAsDate();
        return startTime.before(now) && endTime.after(now);
    }

    /**
     * Returns true if the current time is after the end time of the itinerary.
     */
    public boolean hasEnded() {
        return endTime.before(DateTimeUtils.nowAsDate());
    }

    /**
     * Offsets the start time, end time and all start/end times of each leg by the given value in milliseconds.
     */
    public void offsetTimes(long offsetMillis) {
        startTime = new Date(startTime.getTime() + offsetMillis);
        endTime = new Date(endTime.getTime() + offsetMillis);
        for (Leg leg : legs) {
            leg.startTime = new Date(leg.startTime.getTime() + offsetMillis);
            leg.endTime = new Date(leg.endTime.getTime() + offsetMillis);
        }
    }
}
