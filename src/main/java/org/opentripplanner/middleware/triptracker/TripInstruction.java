package org.opentripplanner.middleware.triptracker;

/**
 * Instructions to be provided to the user depending on where they are on their journey.
 */
public enum TripInstruction {

    GET_ON_BUS,
    STAY_ON_BUS,
    PREPARE_TO_GET_OFF_BUS,
    GET_OFF_BUS,
    NO_INSTRUCTION

    // More instruction types will be added.
}
