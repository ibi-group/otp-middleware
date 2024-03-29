package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.utils.Coordinates;

public class Segment {

    /** The coordinates associated with the start of a segment. */
    public final Coordinates start;

    /** The coordinates associated with the end of a segment. */
    public final Coordinates end;

    /** The time spent in this segment in seconds. */
    public final double timeInSegment;

    /** The leg mode associated with this segment. */
    public final String mode;

    /** The cumulative time since the start of the leg. This includes time in segment. */
    public final double cumulativeTime;

    public Segment(Coordinates start, Coordinates end, double timeInSegment, String mode, double cumulativeTime) {
        this.start = start;
        this.end = end;
        this.timeInSegment = timeInSegment;
        this.mode = mode;
        this.cumulativeTime = cumulativeTime;
    }
}
