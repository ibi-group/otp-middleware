package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Place;

import java.time.Instant;
import java.util.Locale;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;

public class TripInstruction {

    /** The radius in meters under which an immediate instruction is given. */
    public static final int TRIP_INSTRUCTION_IMMEDIATE_RADIUS
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_IMMEDIATE_RADIUS", 2);

    /** The radius in meters under which an upcoming instruction is given. */
    public static final int TRIP_INSTRUCTION_UPCOMING_RADIUS
        = getConfigPropertyAsInt("TRIP_INSTRUCTION_UPCOMING_RADIUS", 10);

    public static final String NO_INSTRUCTION = "NO_INSTRUCTION";

    /** Distance in meters to step instruction or destination. */
    public double distance;

    /** Stop/place aligned with traveler's position. */
    public Place place;

    /** Instruction prefix. */
    public String prefix;

    /** Name of final destination or street. */
    public String locationName;

    /** The time provided by the traveler */
    public Instant currentTime;

    /** The traveler's locale. */
    protected Locale locale;

    protected TripInstruction() {
        // For use by subclasses.
    }

    /**
     * If the traveler is within the upcoming radius an instruction will be provided.
     */
    public boolean hasInstruction() {
        return distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS;
    }

    /**
     * Get the number of minutes to wait for a bus. If the wait is zero (or less than zero!) return empty string.
     */
    protected String getReadableMinutes(long waitInMinutes) {
        if (waitInMinutes == 1) {
            return String.format(" %s minute", waitInMinutes);
        } else if (waitInMinutes > 1) {
            return String.format(" %s minutes", waitInMinutes);
        }
        return "";
    }

    public String build() {
        return NO_INSTRUCTION;
    }
}
