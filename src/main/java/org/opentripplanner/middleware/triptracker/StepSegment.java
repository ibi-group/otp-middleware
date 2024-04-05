package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.utils.Coordinates;

public class StepSegment extends Segment {

    /** Distance in meters between start and end coordinates */
    public final double distance;

    /** Index of step within the trip steps. */
    public final int stepIndex;

    /**
     * Used to hold the nearest step to the traveler's position, and its index. The end coordinate and distance are
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
