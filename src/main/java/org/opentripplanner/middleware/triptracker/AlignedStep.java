package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Step;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

public class AlignedStep {

    /** The distance under which the immediate prefix will be applied. */
    private static final int TRIP_INSTRUCTION_IMMEDIATE_PREFIX_DISTANCE
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_IMMEDIATE_PREFIX_DISTANCE", 2);

    /** The prefix to use when on an instruction. */
    public static final String TRIP_INSTRUCTION_IMMEDIATE_PREFIX
        = getConfigPropertyAsText("TRIP_INSTRUCTION_IMMEDIATE_PREFIX", "IMMEDIATE: ");

    /** The distance under which the upcoming prefix will be applied. */
    private static final int TRIP_INSTRUCTION_UPCOMING_PREFIX_DISTANCE
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_UPCOMING_PREFIX_DISTANCE", 10);

    /** The prefix to use when nearing an instruction. */
    public static final String TRIP_INSTRUCTION_UPCOMING_PREFIX
        = getConfigPropertyAsText("TRIP_INSTRUCTION_UPCOMING_PREFIX", "UPCOMING: ");

    /** Distance to aligned step. */
    public final double distance;

    /** Step aligned with traveler's position. */
    public final Step legStep;

    /** Instruction prefix. */
    public final String prefix;

    public AlignedStep(double distance, Step legStep) {
        this.distance = distance;
        this.legStep = legStep;
        if (distance <= TRIP_INSTRUCTION_IMMEDIATE_PREFIX_DISTANCE) {
            prefix = TRIP_INSTRUCTION_IMMEDIATE_PREFIX;
        } else if (distance <= TRIP_INSTRUCTION_UPCOMING_PREFIX_DISTANCE) {
            prefix = TRIP_INSTRUCTION_UPCOMING_PREFIX;
        } else {
            prefix = null;
        }
    }
}
