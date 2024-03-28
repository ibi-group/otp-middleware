package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.utils.Coordinates;

import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

public class StepSegment extends Segment {

    /** Distance in meters between start and end coordinates */
    public final double distance;

    /** Index of step within the trip steps. */
    public final int stepIndex;

    /**
     * Used to create a step segment.
     *
     * @param start The lat/lon of the current step.
     * @param end The lat/lon of the next step.
     * @param stepIndex The current step index.
     */
    public StepSegment(Coordinates start, Coordinates end, int stepIndex) {
        super(start, end);
        this.distance = getDistance(start, end);
        this.stepIndex = stepIndex;
    }

    /**
     * Used to hold the nearest step to the traveler's position, and it's index. The end coordinate and distance are
     * unknown and irrelevant to downstream processes.
     *
     * @param step The step nearest to the traveler's position.
     * @param stepIndex The index of the step nearest to the traveler's position.
     */
    public StepSegment(Step step, int stepIndex) {
        super(new Coordinates(step), null);
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
