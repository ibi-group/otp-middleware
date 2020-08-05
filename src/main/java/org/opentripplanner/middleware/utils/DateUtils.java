package org.opentripplanner.middleware.utils;

import net.iakovlev.timeshape.TimeZoneEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * Date specific utils
 */
public class DateUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DateUtils.class);

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
            LOG.error("Unable to parse {} : {}.", paramName, paramValue, e);
            throw e;
        }
    }

    public static LocalDate getDateFromString(String value, String expectedDatePattern) throws DateTimeParseException {
        DateTimeFormatter expectedDateFormat = new DateTimeFormatterBuilder()
            .appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.systemDefault());
        try {
            return LocalDate.parse(value, expectedDateFormat);
        } catch (DateTimeParseException e) {
            throw e;
        }
    }

    public static String getStringFromDate(LocalDate localDate, String expectedDatePattern) throws DateTimeParseException {
        DateTimeFormatter expectedDateFormat = new DateTimeFormatterBuilder()
            .appendPattern(expectedDatePattern)
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.systemDefault());
        return localDate.format(expectedDateFormat);
    }
}
