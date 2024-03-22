package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.utils.Coordinates;

import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

public class StepSegment extends Segment {

    /** Distance in meters between start and end coordinates */
    public final double distance;

    /** Index of step within the trip steps. */
    public final int stepIndex;

    public StepSegment(Coordinates start, Coordinates end, int stepIndex) {
        super(start, end);
        this.distance = getDistance(start, end);
        this.stepIndex = stepIndex;
    }

    public StepSegment(Step startStep, int stepIndex) {
        super(new Coordinates(startStep), null);
        this.distance = -1;
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
