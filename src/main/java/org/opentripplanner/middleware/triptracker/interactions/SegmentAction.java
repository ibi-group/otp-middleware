package org.opentripplanner.middleware.triptracker.interactions;

import org.opentripplanner.middleware.triptracker.Segment;
import org.opentripplanner.middleware.utils.Coordinates;

/** Associates a segment (a pair of coordinates, optionally oriented) to an action or handler. */
public class SegmentAction {
    /** Identifier string for this object. */
    public String id;

    /** The starting coordinated of the segment to which the trigger should be applied. */
    public Coordinates start;

    /** The starting coordinated of the segment to which the trigger should be applied. */
    public Coordinates end;

    /** The fully-qualified Java class to execute. */
    public String trigger;

    public SegmentAction() {
        // For persistence.
    }

    public SegmentAction(String id, Segment segment) {
        this(id, segment, null);
    }

    public SegmentAction(String id, Segment segment, String trigger) {
        this.id = id;
        this.start = segment.start;
        this.end = segment.end;
        this.trigger = trigger;
    }
}
