package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.triptracker.TripInstruction;

import java.util.Locale;

/**
 * Instruction to get off a transit vehicle at the current stop or imminently.
 */
public class GetOffHereTransitInstruction extends TripInstruction {

    public GetOffHereTransitInstruction(String stopName, Locale locale) {
        super(0, stopName, locale); // TODO: fix distance arg.
    }

    @Override
    public String build() {
        return String.format("Get off here (%s)", locationName);
    }
}
