package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;

import static org.opentripplanner.middleware.utils.ItineraryUtils.getRouteShortNameFromLeg;

/**
 * Instruction to wait for a transit vehicle, typically emitted when someone is arriving at a transit stop.
 */
public class WaitForTransitInstruction extends TransitLegInstruction {
    private static final Logger LOG = LoggerFactory.getLogger(WaitForTransitInstruction.class);

    public WaitForTransitInstruction(Leg transitLeg, Instant currentTime, Locale locale) {
        this.transitLeg = transitLeg;
        this.currentTime = currentTime;
        this.locale = locale;
    }

    @Override
    public String build() {
        // TODO: i18n
        String routeShortName = getRouteShortNameFromLeg(transitLeg);
        long waitInMinutes = getWaitInMinutes();
        String waitInfo;
        if (waitInMinutes < -2) {
            waitInfo = " (That time has passed)";
        } else if (Boolean.TRUE.equals(transitLeg.realTime)) {
            long delayInMinutes = transitLeg.departureDelay / 60;
            long absoluteMinutes = Math.abs(delayInMinutes);
            String delayInfo = (delayInMinutes > 0) ? "late" : "early";
            waitInfo = (absoluteMinutes <= 1)
                ? ", on time"
                : String.format(" now%s %s", getReadableMinutes(delayInMinutes), delayInfo);
        } else {
            waitInfo = " (No real-time info)";
        }

        String message = String.format(
            "Wait%s for your bus, route %s, scheduled at %s%s",
            getReadableMinutes(waitInMinutes),
            routeShortName,
            DateTimeUtils.formatShortDate(Date.from(transitLeg.getScheduledStartTime().toInstant()), locale),
            waitInfo
        );

        String logDetails = String.format(
            ", t=%d, deptime=%d (+%ds)",
            currentTime.toEpochMilli(),
            transitLeg.startTime.getTime(),
            transitLeg.departureDelay
        );

        LOG.info(message + logDetails);

        return message;
    }

    public long getWaitInMinutes() {
        return Duration.between(
            currentTime.atZone(DateTimeUtils.getOtpZoneId()),
            ZonedDateTime.ofInstant(transitLeg.startTime.toInstant(), DateTimeUtils.getOtpZoneId())
        ).toMinutes();
    }
}
