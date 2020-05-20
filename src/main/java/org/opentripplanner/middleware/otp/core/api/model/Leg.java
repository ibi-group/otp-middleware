package org.opentripplanner.middleware.otp.core.api.model;

import java.util.*;

/**
 * One leg of a trip -- that is, a temporally continuous piece of the journey that takes place on a
 * particular vehicle (or on foot).
 */

public class Leg {

    /**
     * The date and time this leg begins.
     */
    public Date startTime = null;

    /**
     * The date and time this leg ends.
     */
    public Date endTime = null;

    /**
     * For transit leg, the offset from the scheduled departure-time of the boarding stop in this leg.
     * "scheduled time of departure at boarding stop" = startTime - departureDelay
     */
    public int departureDelay = 0;
    /**
     * For transit leg, the offset from the scheduled arrival-time of the alighting stop in this leg.
     * "scheduled time of arrival at alighting stop" = endTime - arrivalDelay
     */
    public int arrivalDelay = 0;
    /**
     * Whether there is real-time data about this Leg
     */
    public Boolean realTime = false;

    /**
     * The distance traveled while traversing the leg in meters.
     */
    public Double distance = null;

    /**
     * Is this leg a traversing pathways?
     */
    public Boolean pathway = false;

    /**
     * The mode (e.g., <code>Walk</code>) used when traversing this leg.
     */
    public String mode;

    /**
     * The Place where the leg originates.
     */
    public Place from = null;

    /**
     * The Place where the leg begins.
     */
    public Place to = null;

    @Override
    public String toString() {
        return "Leg{" +
            "startTime=" + startTime +
            ", endTime=" + endTime +
            ", departureDelay=" + departureDelay +
            ", arrivalDelay=" + arrivalDelay +
            ", realTime=" + realTime +
            ", distance=" + distance +
            ", pathway=" + pathway +
            ", mode='" + mode + '\'' +
            ", from=" + from +
            ", to=" + to +
            '}';
    }
}
