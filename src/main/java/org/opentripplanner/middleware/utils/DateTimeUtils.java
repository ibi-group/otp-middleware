package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Date;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Date and time specific utils. All timing in this application should be obtained by using this method in order to
 * ensure that the correct system clock is used. During testing, the internal clock is often set to a fixed instant to
 * test time-dependent code.
 */
public class DateTimeUtils {
    private static final Logger LOG = LoggerFactory.getLogger(DateTimeUtils.class);

    public static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    public static final DateTimeFormatter NOTIFICATION_TIME_FORMATTER = DateTimeFormatter.ofPattern(
        ConfigUtils.getConfigPropertyAsText("NOTIFICATION_TIME_FORMAT", "HH:mm")
    );

    /**
     * These are internal variables that can be used to mock dates and times in tests
     */
    private static Clock clock = Clock.systemDefaultZone();
    private static ZoneId zoneId = clock.getZone();

    /**
     * Get {@link java.time.LocalDate} from provided value base on expected date format. The date conversion
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

    /**
     * Get the configured timezone that OTP is using from the config. OTP parses dates and times assuming the use of the
     * timezone identifier of the first agency that it finds.
     */
    public static ZoneId getOtpZoneId() {
        String otpTzId = getConfigPropertyAsText("OTP_TIMEZONE");
        if (otpTzId == null) {
            throw new RuntimeException("OTP_TIMEZONE is not defined in config!");
        }
        return ZoneId.of(otpTzId);
    }

    /**
     * Converts a {@link LocalDateTime} on OTP's time zone to epoch milliseconds.
     */
    public static long otpDateTimeAsEpochMillis(LocalDateTime otpDateTime) {
        return Instant.from(ZonedDateTime.of(otpDateTime, getOtpZoneId())).toEpochMilli();
    }

    /**
     * Converts a {@link LocalDate} object from the 'date' query parameter string,
     * or returns today's date if that parameter is null.
     */
    public static LocalDate getDateFromQueryDateString(String dateString) {
        return dateString == null
            ? nowAsLocalDate()
            : getDateFromString(dateString, DEFAULT_DATE_FORMAT_PATTERN);
    }

    /**
     * Makes a {@link ZonedDateTime} object from a date string and a time string, using OTP's time zone.
     */
    public static ZonedDateTime makeOtpZonedDateTime(String dateString, String timeString) {
        return ZonedDateTime.of(
            getDateFromString(dateString, DEFAULT_DATE_FORMAT_PATTERN),
            LocalTime.parse(timeString),
            DateTimeUtils.getOtpZoneId()
        );
    }

    public static ZonedDateTime makeOtpZonedDateTime(Date date) {
        return ZonedDateTime.ofInstant(date.toInstant(), getOtpZoneId());
    }
}
