package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.utils.Coordinates;

public class Segment {

    /** The coordinates associated with the start of a segment. */
    public final Coordinates start;

    /** The coordinates associated with the end of a segment. */
    public final Coordinates end;

    public Segment(Coordinates start, Coordinates end) {
        this.start = start;
        this.end = end;
    }
}
