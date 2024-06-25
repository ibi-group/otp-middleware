package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.triptracker.TripInstruction;

import java.util.Locale;

/**
 * Instruction to prepare to get off a transit vehicle.
 */
public class GetOffSoonTransitInstruction extends TripInstruction {

    public GetOffSoonTransitInstruction(String stopName, Locale locale) {
        this.locationName = stopName;
        this.locale = locale;
    }

    @Override
    public String build() {
        // TODO: i18n
        return String.format("Your stop is coming up (%s)", locationName);
    }
}
