package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;

/**
 * An Itinerary is one complete way of getting from the start location to the end location.
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Itinerary {

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
     * This method calculates equality in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries match.
     *
     * FIXME: maybe don't check duration exactly as it might vary slightly in certain trips
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Itinerary itinerary = (Itinerary) o;
        return duration.equals(itinerary.duration) &&
            Objects.equals(transfers, itinerary.transfers) &&
            Objects.equals(legs, itinerary.legs);
    }

    /**
     * This method calculates the hash code in the context of trip monitoring in order to analyzing equality when
     * checking if itineraries match.
     *
     * FIXME: maybe don't check duration exactly as it might vary slightly in certain trips
     */
    @Override
    public int hashCode() {
        return Objects.hash(duration, transfers, legs);
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
}
