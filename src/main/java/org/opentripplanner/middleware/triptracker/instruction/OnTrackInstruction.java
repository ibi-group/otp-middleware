package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Step;

import java.util.Locale;

/** Instruction for cases someone is on track in their itinerary */
public class OnTrackInstruction extends SelfLegInstruction {
    /** The prefix to use when at a street location with an instruction. */
    public static final String TRIP_INSTRUCTION_IMMEDIATE_PREFIX = "IMMEDIATE: ";

    /** The prefix to use when nearing a street location with an instruction. */
    public static final String TRIP_INSTRUCTION_UPCOMING_PREFIX = "UPCOMING: ";

    /** The prefix to use when arrived at the destination. */
    public static final String TRIP_INSTRUCTION_ARRIVED_PREFIX = "ARRIVED: ";

    public OnTrackInstruction(boolean isDestination, double distance, Locale locale) {
        this.distance = distance;
        this.locale = locale;
        setPrefix(isDestination);
    }

    /**
     * On track instruction to step.
     */
    public OnTrackInstruction(double distance, Step legStep, Locale locale) {
        this(false, distance, locale);
        this.legStep = legStep;
    }

    /**
     * On track instruction to destination.
     */
    public OnTrackInstruction(double distance, String locationName, Locale locale) {
        this(true, distance, locale);
        this.locationName = locationName;
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
     * Build on track instruction based on step instructions and location. e.g.
     * <p>
     * "UPCOMING: CONTINUE on Langley Drive"
     * "IMMEDIATE: RIGHT on service road"
     * "ARRIVED: Gwinnett Justice Center (Central)"
     * <p>
     * TODO: Internationalization and refinements to these generated instructions with input from the mobile app team.
     */
    @Override
    public String build() {
        if (hasInstruction()) {
            if (legStep != null) {
                String relativeDirection = (legStep.relativeDirection.equals("DEPART"))
                    ? "Head " + legStep.absoluteDirection
                    : legStep.relativeDirection;
                return String.format("%s%s on %s", prefix, relativeDirection, legStep.streetName);
            } else if (locationName != null) {
                return String.format("%s%s", prefix, locationName);
            }
        }
        return NO_INSTRUCTION;
    }
}
