package org.opentripplanner.middleware.triptracker;

/**
 * Instructions to be provided to the user depending on where they are on their journey.
 */
public enum TripInstruction {

    GET_ON_BUS,
    STAY_ON_BUS,
    PREPARE_TO_GET_OFF_BUS,
    GET_OFF_BUS,
    NO_INSTRUCTION;

    // More instruction types will be added.

    /**
     * Provides the instructions for the user based on the trip stage and location.
     */
    public static String getInstructions(TripStage tripStage) {
        // This is to be expanded on in later PRs. For now, it is used for unit testing.
        switch (tripStage) {
            case START:
                return TripInstruction.GET_ON_BUS.name();
            case UPDATE:
                return TripInstruction.STAY_ON_BUS.name();
            default:
                return TripInstruction.NO_INSTRUCTION.name();
        }
    }
}
