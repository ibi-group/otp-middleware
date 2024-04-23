package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.utils.Coordinates;

import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

public class LegSegment extends Segment {

    /** The time spent in this segment in seconds. */
    public double timeInSegment;

    /** The leg mode associated with this segment. */
    public final String mode;

    /** The cumulative time since the start of the leg. This includes time in segment. */
    public double cumulativeTime;

    /** Distance in meters between start and end coordinates */
    public final double distance;

    public LegSegment(Coordinates start, Coordinates end, double timeInSegment, String mode, double cumulativeTime) {
        super(start, end);
        this.timeInSegment = timeInSegment;
        this.mode = mode;
        this.cumulativeTime = cumulativeTime;
        this.distance = getDistance(start, end);
    }

    /**
     * Used to hold details of the nearest leg segment to the traveler. Used when a traveler has deviated from trip.
     */
    public LegSegment(double distance, String mode) {
        super(null, null);
        this.distance = distance;
        this.mode = mode;
    }

    @Override
    public String toString() {
        return String.format(
            "LegSegment {timeInSegment=%s, mode=%s, cumulativeTime=%s, distance=%s}",
            timeInSegment,
            mode,
            cumulativeTime,
            distance
        );
    }
}
