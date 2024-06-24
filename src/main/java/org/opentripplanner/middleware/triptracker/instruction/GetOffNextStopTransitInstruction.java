package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.triptracker.TripInstruction;

import java.util.Locale;

/**
 * Instruction to get off a transit vehicle at the next stop.
 */
public class GetOffNextStopTransitInstruction extends TripInstruction {

    public GetOffNextStopTransitInstruction(double distance, String stopName, Locale locale) {
        super(distance, stopName, locale); // TODO: fix distance arg.
    }

    @Override
    public String build() {
        return String.format("Get off at next stop (%s)", locationName);
    }
}
