package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Step;

/**
 * Parent class for instructions on legs where the user is in charge of where they are going (walk, bike, scooter),
 * as opposed to a transit or taxi leg where user has no control of where the vehicle goes.
 */
public class SelfLegInstruction extends TripInstruction {
    /** Step aligned with traveler's position. */
    protected Step legStep;

    public Step getLegStep() {
        return legStep;
    }

}
