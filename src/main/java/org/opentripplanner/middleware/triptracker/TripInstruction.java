package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Step;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

public class TripInstruction {

    /** The radius in meters under which an immediate instruction is given. */
    public static final int TRIP_INSTRUCTION_IMMEDIATE_RADIUS
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_IMMEDIATE_RADIUS", 2);

    /** The radius in meters under which an upcoming instruction is given. */
    public static final int TRIP_INSTRUCTION_UPCOMING_RADIUS
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_UPCOMING_RADIUS", 10);

    /** The prefix to use when on an instruction. */
    public static final String TRIP_INSTRUCTION_IMMEDIATE_PREFIX = "IMMEDIATE: ";

    /** The prefix to use when nearing an instruction. */
    public static final String TRIP_INSTRUCTION_UPCOMING_PREFIX = "UPCOMING: ";

    /** The prefix to use when arrived at the destination. */
    public static final String TRIP_INSTRUCTION_ARRIVED_PREFIX = "ARRIVED: ";

    public static final String NO_INSTRUCTION = "NO_INSTRUCTION";

    /** Distance in meters to step instruction or destination. */
    public final double distance;

    /** Step aligned with traveler's position. */
    public Step legStep;

    /** Instruction prefix. */
    public String prefix;

    /** Name of final destination. */
    public String destinationName;

    public TripInstruction(double distance, Step legStep) {
        this.distance = distance;
        this.legStep = legStep;
        setPrefix(false);
    }

    public TripInstruction(double distance, String destinationName) {
        this.distance = distance;
        this.destinationName = destinationName;
        setPrefix(true);
    }

    /**
     * The prefix is defined depending on the traveler either approaching a step or destination and the predefined
     * distances from these points.
     */
    private void setPrefix(boolean isDestination) {
        if (distance <= TRIP_INSTRUCTION_IMMEDIATE_RADIUS) {
            prefix = (isDestination) ? TRIP_INSTRUCTION_ARRIVED_PREFIX : TRIP_INSTRUCTION_IMMEDIATE_PREFIX;
        } else if (distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS) {
            prefix = TRIP_INSTRUCTION_UPCOMING_PREFIX;
        }
    }

    /**
     * Build instruction based on distance and step instructions. e.g.
     * <p>
     * "UPCOMING: CONTINUE on Langley Drive"
     * "IMMEDIATE: RIGHT on service road"
     * "IMMEDIATE: Head WEST on sidewalk"
     * "ARRIVED: Gwinnett Justice Center (Central)"
     * <p>
     * TODO: Internationalization and refinements to these generated instructions with input from the mobile app team.
     */
    public String build() {
        if (prefix != null) {
            if (legStep != null) {
                String relativeDirection = (legStep.relativeDirection.equals("DEPART"))
                    ? "Head " + legStep.absoluteDirection
                    : legStep.relativeDirection;
                return String.format("%s%s on %s", prefix, relativeDirection, legStep.streetName);
            } else if (destinationName != null) {
                return String.format("%s%s", prefix, destinationName);
            }
        }
        return NO_INSTRUCTION;
    }
}
