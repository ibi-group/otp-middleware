package org.opentripplanner.middleware.triptracker.instruction;

import java.util.Locale;

/** Instruction when someone is deviated from their route */
public class DeviatedInstruction extends SelfLegInstruction {
    public DeviatedInstruction(String referenceLocation, Locale locale) {
        this.locationName = referenceLocation;
        this.locale = locale;
    }

    @Override
    public String build() {
        // TODO: i18n
        return String.format("Head to %s", locationName);
    }
}
