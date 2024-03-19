package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.utils.Coordinates;

import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

public class StepSegment {

    /** The coordinates associated with the start of a segment. */
    public final Coordinates start;

    /** The coordinates associated with the end of a segment. */
    public final Coordinates end;

    /** Distance in meters between start and end coordinates */
    public final double distance;

    /** Index of step within the trip steps. */
    public final int stepIndex;

    public StepSegment(Coordinates start, Coordinates end, int stepIndex) {
        this.start = start;
        this.end = end;
        this.distance = getDistance(start, end);
        this.stepIndex = stepIndex;
    }

    @Override
    public String toString() {
        return "StepSegment{" +
            "start=" + start +
            ", end=" + end +
            ", distance=" + distance +
            ", stepIndex=" + stepIndex +
            '}';
    }
}
