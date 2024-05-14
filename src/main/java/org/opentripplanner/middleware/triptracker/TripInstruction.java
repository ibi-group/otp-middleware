package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Step;

import java.time.Duration;
import java.time.Instant;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

public class TripInstruction {

    public enum TripInstructionType { ON_TRACK, DEVIATED, WAIT_FOR_BUS }

    /** The radius in meters under which an immediate instruction is given. */
    public static final int TRIP_INSTRUCTION_IMMEDIATE_RADIUS
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_IMMEDIATE_RADIUS", 2);

    /** The radius in meters under which an upcoming instruction is given. */
    public static final int TRIP_INSTRUCTION_UPCOMING_RADIUS
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_UPCOMING_RADIUS", 10);

    /** The prefix to use when at a street location with an instruction. */
    public static final String TRIP_INSTRUCTION_IMMEDIATE_PREFIX = "IMMEDIATE: ";

    /** The prefix to use when nearing a street location with an instruction. */
    public static final String TRIP_INSTRUCTION_UPCOMING_PREFIX = "UPCOMING: ";

    /** The prefix to use when arrived at the destination. */
    public static final String TRIP_INSTRUCTION_ARRIVED_PREFIX = "ARRIVED: ";

    public static final String NO_INSTRUCTION = "NO_INSTRUCTION";

    /** Distance in meters to step instruction or destination. */
    public double distance;

    /** Step aligned with traveler's position. */
    public Step legStep;

    /** Instruction prefix. */
    public String prefix;

    /** Name of final destination or street. */
    public String locationName;

    /** Provided if the next leg for the traveler will be a bus transit leg. */
    public Leg busLeg;

    /** The time provided by the traveler */
    public Instant currentTime;

    TripInstructionType tripInstructionType;

    public TripInstruction(boolean isDestination, double distance) {
        this.distance = distance;
        this.tripInstructionType = TripInstructionType.ON_TRACK;
        setPrefix(isDestination);
    }

    /**
     * If the traveler is within the upcoming radius an instruction will be provided.
     */
    public boolean hasInstruction() {
        return distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS;
    }

    /**
     * On track instruction to step.
     */
    public TripInstruction(double distance, Step legStep) {
        this(false, distance);
        this.legStep = legStep;
    }

    /**
     * On track instruction to destination.
     */
    public TripInstruction(double distance, String locationName) {
        this(true, distance);
        this.locationName = locationName;
    }

    /**
     * Deviated instruction.
     */
    public TripInstruction(String locationName) {
        this.tripInstructionType = TripInstructionType.DEVIATED;
        this.locationName = locationName;
    }

    /**
     * Provide bus related trip instruction.
     */
    public TripInstruction(Leg busLeg, Instant currentTime) {
        this.tripInstructionType = TripInstructionType.WAIT_FOR_BUS;
        this.busLeg = busLeg;
        this.currentTime = currentTime;
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
     * Build instruction based on the traveler's location.
     */
    public String build() {
        switch (tripInstructionType) {
            case ON_TRACK:
                return buildOnTrackInstruction();
            case DEVIATED:
                return String.format("Head to %s", locationName);
            case WAIT_FOR_BUS:
                return buildWaitForBusInstruction();
            default:
                return NO_INSTRUCTION;
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

    private String buildOnTrackInstruction() {
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

    /**
     * Build wait for bus instruction.
     */
    private String buildWaitForBusInstruction() {
        String routeId = busLeg.routeId.split(":")[1];
        if (busLeg.departureDelay > 0) {
            long waitInMinutes = Duration.between(busLeg.getScheduledStartTime(), currentTime).toMinutes();
            return String.format("Wait %s minute(s) for bus %s", waitInMinutes, routeId);
        } else {
            return String.format("Wait for bus %s scheduled to arrive at %s", routeId, busLeg.getScheduledStartTime());
        }
    }
}
