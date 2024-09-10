package org.opentripplanner.middleware.utils;

import jersey.repackaged.com.google.common.collect.Lists;
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
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Date and time specific utils. All timing in this application should be obtained by using this method in order to
 * ensure that the correct system clock is used. During testing, the internal clock is often set to a fixed instant to
 * test time-dependent code.
 */
public class DateTimeUtils {

    private DateTimeUtils() {
        throw new IllegalStateException("Utility class.");
    }

    private static final Logger LOG = LoggerFactory.getLogger(DateTimeUtils.class);

    public static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    public static final DateTimeFormatter DEFAULT_DATE_FORMATTER = DateTimeFormatter.ofPattern(
        DEFAULT_DATE_FORMAT_PATTERN
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

    public static String getStringFromDate(LocalDateTime localDate, String expectedDatePattern) throws DateTimeParseException {
        DateTimeFormatter expectedDateFormat = new DateTimeFormatterBuilder()
            .appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(zoneId);
        return localDate.format(expectedDateFormat);
    }

    /**
     * Helper to format a date in short format (e.g. "5:40 PM" - no seconds) in the specified locale.
     */
    public static String formatShortDate(Date date, Locale locale) {
        return DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(DateTimeUtils.getOtpZoneId())
            .format(date.toInstant());
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
            nowAsLocalDateTime(),
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
            return ZoneId.systemDefault();
        }
        return ZoneId.of(otpTzId);
    }

    /**
     * Converts a {@link LocalDateTime} in OTP's time zone to epoch milliseconds.
     */
    public static long otpDateTimeAsEpochMillis(LocalDateTime otpDateTime) {
        return Instant.from(ZonedDateTime.of(otpDateTime, getOtpZoneId())).toEpochMilli();
    }

    /**
     * Calculates the epoch milliseconds in OTP's time zone from the given parameters. The parameters match those found
     * in a constructor for {@link LocalDateTime}.
     */
    public static long otpDateTimeAsEpochMillis(int year, int month, int dayOfMonth, int hour, int minute, int second) {
        return Instant.from(
            ZonedDateTime.of(
                LocalDateTime.of(year, month, dayOfMonth, hour, minute, second),
                getOtpZoneId()
            )
        ).toEpochMilli();
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

    /**
     * Converts a {@link LocalDateTime} object into a {@link Date} object using the Otp zone id.
     */
    public static Date convertToDate(LocalDateTime dateToConvert) {
        return Date.from(dateToConvert.atZone(getOtpZoneId()).toInstant());
    }

    /**
     * Converts a {@link Date} object into a {@link LocalDateTime} object using the Otp zone id.
     */
    public static LocalDateTime convertToLocalDateTime(Date dateToConvert) {
        return dateToConvert.toInstant()
            .atZone(getOtpZoneId())
            .toLocalDateTime();
    }

    /**
     * Get the start of an hour from a {@link Date} object which is returned as a {@link LocalDateTime} object.
     */
    public static LocalDateTime getStartOfHour(Date dateToConvert) {
        return convertToLocalDateTime(dateToConvert).truncatedTo(ChronoUnit.HOURS);
    }

    /**
     * Get the start of an hour from a {@link LocalDateTime} object which is returned as a {@link Date} object.
     */
    public static Date getStartOfHour(LocalDateTime date) {
        return convertToDate(date.truncatedTo(ChronoUnit.HOURS));
    }

    /**
     * Get the end of an hour from a {@link LocalDateTime} object which is returned as a {@link Date} object.
     */
    public static Date getEndOfHour(LocalDateTime date) {
        return convertToDate(date.truncatedTo(ChronoUnit.HOURS).plusHours(1).minusSeconds(1));
    }

    /**
     * Get the start of the current hour.
     */
    public static LocalDateTime getStartOfCurrentHour() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * Get the hours between two {@link LocalDateTime} objects, excluding the start and end hours.
     */
    public static List<LocalDateTime> getHoursBetween(LocalDateTime start, LocalDateTime end) {
        return getTimeUnitsBetween(start, end, ChronoUnit.HOURS);
    }

    /**
     * Get the days between two {@link LocalDateTime} objects, excluding start and end days.
     */
    public static List<LocalDateTime> getDaysBetween(LocalDateTime start, LocalDateTime end) {
        return getTimeUnitsBetween(start, end, ChronoUnit.DAYS);
    }

    /**
     * Get the time units (e.g. days, hours) between two {@link LocalDateTime} objects, excluding start and end.
     */
    public static List<LocalDateTime> getTimeUnitsBetween(
        LocalDateTime start,
        LocalDateTime end,
        ChronoUnit chronoUnit
    ) {
        if (start.isAfter(end) || start.isEqual(end)) {
            LOG.warn("Start date/time: {} is after/equal to end date/time: {}.", start, end);
            return Lists.newArrayList();
        }
        // Bump the start by one day, so it is not included in the returned list.
        start = start.plus(1, chronoUnit).truncatedTo(chronoUnit);
        return Stream
            .iterate(start, date -> date.plus(1, chronoUnit))
            .limit(chronoUnit.between(start, end))
            .collect(Collectors.toList());
    }

    /**
     * Return the previous whole hour from a given date. E.g. If the time is 07:30, return 06:00.
     */
    public static LocalDateTime getPreviousWholeHourFrom(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.HOURS).minusHours(1);
    }

    /**
     * Return the previous day from now. E.g. If today is Wednesday 07:30, return Tuesday.
     */
    public static LocalDateTime getPreviousDayFrom(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.DAYS).minusDays(1);
    }

    /**
     * Converts a date provided in seconds to a date in milliseconds.
     */
    public static Date convertDateFromSecondsToMillis(Date date) {
        return Date.from(Instant.ofEpochSecond(date.getTime()));
    }
}
