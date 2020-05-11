package org.opentripplanner.middleware.otp.core.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opentripplanner.middleware.otp.core.routing.core.Fare;

import java.util.Date;

/**
 * An Itinerary is one complete way of getting from the start location to the end location.
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
     * The cost of this trip
     */
    public Fare fare;

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
                '}';
    }
}
