package org.opentripplanner.middleware.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

public class DateUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DateUtils.class);

    /**
     * Get {@Link java.time.LocalDate} from provided value base on expected date format.
     */
    public static LocalDate getDateFromParam(String paramName, String paramValue, String expectedDatePattern) throws DateTimeParseException {

        // no date value to work with
        if (paramValue == null) {
            return null;
        }

        DateTimeFormatter expectDateFormat = new DateTimeFormatterBuilder().appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.systemDefault());

        LocalDate date;
        try {
            date = LocalDate.parse(paramValue, expectDateFormat);
        } catch (DateTimeParseException e) {
            LOG.error("Unable to parse {} : {}.", paramName, paramValue, e);
            throw e;
        }

        return date;
    }


}
