package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Leg;

import java.util.Locale;

/**
 * Instruction that summarizes a transit leg, emitted typically after getting onboard a transit vehicle.
 */
public class TransitLegSummaryInstruction extends TransitLegInstruction {
    public TransitLegSummaryInstruction(Leg leg, Locale locale) {
        this.transitLeg = leg;
        this.locale = locale;
    }

    @Override
    public String build() {
        // TODO: i18n
        return String.format(
            "Ride %d min / %d stops to %s",
            // Use Math.floor to be consistent with UI for transit leg durations.
            (int)(Math.floor(transitLeg.duration / 60)),
            // OTP returns an empty list if there are no intermediate stops.
            transitLeg.intermediateStops.size() + 1,
            transitLeg.to.name
        );
    }
}
