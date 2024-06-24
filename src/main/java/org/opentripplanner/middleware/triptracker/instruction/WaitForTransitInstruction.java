package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.triptracker.TripInstruction;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import static org.opentripplanner.middleware.utils.ItineraryUtils.getRouteShortNameFromLeg;

/**
 * Instruction to wait for a transit vehicle, typically emitted when someone is arriving at a transit stop.
 */
public class WaitForTransitInstruction extends TripInstruction {
    public WaitForTransitInstruction(Leg transitLeg, Instant currentTime, Locale locale) {
        super(transitLeg, currentTime, locale);
    }

    @Override
    public String build() {
        String routeShortName = getRouteShortNameFromLeg(transitLeg);
        long delayInMinutes = transitLeg.departureDelay;
        long absoluteMinutes = Math.abs(delayInMinutes);
        long waitInMinutes = Duration
            .between(currentTime.atZone(DateTimeUtils.getOtpZoneId()), transitLeg.getScheduledStartTime())
            .toMinutes();
        String delayInfo = (delayInMinutes > 0) ? "late" : "early";
        String arrivalInfo = (absoluteMinutes <= 1)
            ? ", on time"
            : String.format(" now%s %s", getReadableMinutes(delayInMinutes), delayInfo);
        return String.format(
            "Wait%s for your bus, route %s, scheduled at %s%s",
            getReadableMinutes(waitInMinutes),
            routeShortName,
            DateTimeUtils.formatShortDate(Date.from(transitLeg.getScheduledStartTime().toInstant()), locale),
            arrivalInfo
        );
    }
}
