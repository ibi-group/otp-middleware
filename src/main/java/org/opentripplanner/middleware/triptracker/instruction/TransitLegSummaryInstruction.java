package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.triptracker.TripInstruction;

import java.util.Locale;

/**
 * Instruction that summarizes a transit leg, emitted typically after getting onboard a transit vehicle.
 */
public class TransitLegSummaryInstruction extends TripInstruction {
    public TransitLegSummaryInstruction(Leg leg, Locale locale) {
        super(leg.to.name, locale);
        this.busLeg = leg;
    }

    @Override
    public String build() {
        return String.format(
            "Ride %d min / %d stops to %s",
            // Use Math.floor to be consistent with UI for transit leg durations.
            (int)(Math.floor(busLeg.duration / 60)),
            // OTP returns an empty list if there are no intermediate stops.
            busLeg.intermediateStops.size() + 1,
            locationName
        );
    }
}
