package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.triptracker.TripInstruction;

import java.util.Locale;

/**
 * Instruction to get off a transit vehicle at the next stop.
 */
public class GetOffNextStopTransitInstruction extends TripInstruction {

    public GetOffNextStopTransitInstruction(String stopName, Locale locale) {
        this.locationName = stopName;
        this.locale = locale;
    }

    @Override
    public String build() {
        // TODO: i18n
        return String.format("Get off at next stop (%s)", locationName);
    }
}
