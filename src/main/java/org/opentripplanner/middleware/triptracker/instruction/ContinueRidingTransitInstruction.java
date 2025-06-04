package org.opentripplanner.middleware.triptracker.instruction;

/**
 * Instruction that summarizes a transit leg, emitted typically after getting onboard a transit vehicle.
 */
public class ContinueRidingTransitInstruction extends TransitLegInstruction {
    @Override
    public String build() {
        // TODO: i18n and various transit modes (trains, subways...)
        return "Continue riding the bus.";
    }
}
