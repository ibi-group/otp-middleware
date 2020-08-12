package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.bugsnag.BugsnagReporter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

/**
 * Date specific utils
 */
public class DateUtils {

    public static final String YYYY_MM_DD = "yyyy-MM-dd";

    /**
     * Get {@Link java.time.LocalDate} from provided value base on expected date format. The date conversion
     * is based on the system time zone.
     */
    public static LocalDate getDateFromParam(String paramName, String paramValue, String expectedDatePattern)
        throws DateTimeParseException {

        // no date value to work with
        if (paramValue == null) {
            return null;
        }

        DateTimeFormatter expectedDateFormat = new DateTimeFormatterBuilder()
            .appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.systemDefault());

        LocalDate date;
        try {
            date = LocalDate.parse(paramValue, expectedDateFormat);
        } catch (DateTimeParseException e) {
            BugsnagReporter.reportErrorToBugsnag(String.format("Unable to parse date from %s", paramName), paramValue, e);
            throw e;
        }

        return date;
    }


}
