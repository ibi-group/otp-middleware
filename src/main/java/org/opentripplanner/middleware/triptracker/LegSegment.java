package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.utils.Coordinates;

import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

public class LegSegment extends Segment {

    /** The time spent in this segment in seconds. */
    public final double timeInSegment;

    /** The leg mode associated with this segment. */
    public final String mode;

    /** The cumulative time since the start of the leg. This includes time in segment. */
    public final double cumulativeTime;

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
     * Used for testing only.
     */
    public LegSegment(Coordinates start, Coordinates end) {
        super(start, end);
        this.timeInSegment = -1;
        this.mode = null;
        this.cumulativeTime = -1;
        this.distance = -1;
    }

    @Override
    public String toString() {
        return "LegSegment{" +
            "timeInSegment=" + timeInSegment +
            ", mode='" + mode + '\'' +
            ", cumulativeTime=" + cumulativeTime +
            ", distance=" + distance +
            ", start=" + start +
            ", end=" + end +
            '}';
    }
}
