package org.opentripplanner.middleware.utils;

import net.iakovlev.timeshape.TimeZoneEngine;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Optional;

/**
 * Date and time specific utils. All timing in this application should be obtained by using this method in order to
 * ensure that the correct system clock is used. During testing, the internal clock is often set to a fixed instant to
 * test time-dependent code.
 */
public class DateTimeUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DateTimeUtils.class);

    public static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";

    /**
     * These are internal variables that can be used to mock dates and times in tests
     */
    private static Clock clock = Clock.systemDefaultZone();
    private static ZoneId zoneId = clock.getZone();

    /**
     * Timezone engine is used to look up the timezone based on input lat/lng coordinates.
     * TODO: If this is memory-intensive we may want to limit it by a bounding box.
     */
    private static final TimeZoneEngine engine = TimeZoneEngine.initialize();

    public static Optional<ZoneId> getZoneIdForCoordinates(double lat, double lon) {
        return engine.query(lat, lon);
    }

    /**
     * Get {@Link java.time.LocalDate} from provided value base on expected date format. The date conversion
     * is based on the system time zone.
     */
    public static LocalDate getDateFromParam(String paramName, String paramValue, String expectedDatePattern)
        throws DateTimeParseException {
        try {
            return getDateFromString(paramValue, expectedDatePattern);
        } catch (DateTimeParseException e) {
            BugsnagReporter.reportErrorToBugsnag(String.format("Unable to parse date from %s", paramName), paramValue, e);
            throw e;
        }
    }

    public static LocalDate getDateFromString(String value, String expectedDatePattern) throws DateTimeParseException {
        DateTimeFormatter expectedDateFormat = new DateTimeFormatterBuilder()
            .appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(zoneId);
        try {
            return LocalDate.parse(value, expectedDateFormat);
        } catch (DateTimeParseException e) {
            BugsnagReporter.reportErrorToBugsnag("Unable to parse LocalDate", value, e);
            throw e;
        }
    }

    public static String getStringFromDate(LocalDate localDate, String expectedDatePattern) throws DateTimeParseException {
        DateTimeFormatter expectedDateFormat = new DateTimeFormatterBuilder()
            .appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(zoneId);
        return localDate.format(expectedDateFormat);
    }

    public static Date nowAsDate() {
        return new Date(currentTimeMillis());
    }

    public static LocalDate nowAsLocalDate() {
        return LocalDate.now(clock);
    }

    /**
     * Returns the current LocalDateTime according to the currently set Clock.
     */
    public static LocalDateTime nowAsLocalDateTime() {
        return LocalDateTime.now(clock);
    }

    /**
     * Returns the current time according to the currently set Clock given a specific timezone.
     */
    public static LocalDateTime nowAsLocalDateTime(ZoneId zoneId) {
        return LocalDateTime.now(clock.withZone(zoneId));
    }

    /**
     * Returns the current time as a ZonedDateTime instance in the given timezone
     */
    public static ZonedDateTime nowAsZonedDateTime(ZoneId zoneId) {
        return ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
    }

    /**
     * Returns the current time in milliseconds according the the current set Clock.
     */
    public static long currentTimeMillis() {
        return clock.millis();
    }

    /**
     * Mocks the current time using the provided dateTime and internally set timezone
     */
    public static void useFixedClockAt(ZonedDateTime zonedDateTime) {
        zoneId = zonedDateTime.getZone();
        clock = Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.getZone());
        logCurrentTimeAndZone("Internal clock is now using a fixed clock time!");
    }

    /**
     * Resets the internal clock instance to the default system timekeeping method
     */
    public static void useSystemDefaultClockAndTimezone(){
        clock = Clock.systemDefaultZone();
        zoneId = clock.getZone();
        logCurrentTimeAndZone("Internal clock has been reset to the default system clock!");
    }

    private static void logCurrentTimeAndZone(String messagePrefix) {
        LOG.info(
            "{} The current time is now: {} ({})",
            messagePrefix,
            nowAsLocalDateTime().toString(),
            zoneId.getId()
        );
    }

    /**
     * Returns the internally set timezone
     */
    public static ZoneId getSystemZoneId() {
        return zoneId;
    }
}
