package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;

/**
 * This class holds an {@link ItineraryExistenceResult} for each day of the week,
 * so that clients can determine whether a trip can be regularly monitored on that
 * particular day of the week.
 */
public class ItineraryExistence {
    public ItineraryExistenceResult monday;
    public ItineraryExistenceResult tuesday;
    public ItineraryExistenceResult wednesday;
    public ItineraryExistenceResult thursday;
    public ItineraryExistenceResult friday;
    public ItineraryExistenceResult saturday;
    public ItineraryExistenceResult sunday;
    /**
     * When the itinerary existence check was run/completed.
     * FIXME: If a monitored trip has not been fully enabled for monitoring, we may want to check the timestamp to
     *  verify that the existence check has not gone stale.
     */
    public Date timestamp = new Date();

    public ItineraryExistence(Set<ZonedDateTime> datesChecked, Map<ZonedDateTime, Itinerary> datesWithMatches) {
        // Initialize each day according to the dates checked.
        for (ZonedDateTime date : datesChecked) {
            ItineraryExistenceResult result = new ItineraryExistenceResult();
            setResultForDayOfWeek(result, date.getDayOfWeek());

            // If no match was found for that date, mark date as non-available for the desired trip.
            Itinerary itineraryForCheckedDate = datesWithMatches.get(date);
            if (itineraryForCheckedDate == null) {
                result.handleInvalidDate(date);
            } else {
                result.itinerary = itineraryForCheckedDate;
            }
        }
    }

    public ItineraryExistenceResult getResultForDayOfWeek(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return monday;
            case TUESDAY: return tuesday;
            case WEDNESDAY: return wednesday;
            case THURSDAY: return thursday;
            case FRIDAY: return friday;
            case SATURDAY: return saturday;
            case SUNDAY: return sunday;
        }
        throw new IllegalArgumentException("Invalid day of week provided!");
    }

    public void setResultForDayOfWeek(ItineraryExistenceResult result, DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                monday = result;
                break;
            case TUESDAY:
                tuesday = result;
                break;
            case WEDNESDAY:
                wednesday = result;
                break;
            case THURSDAY:
                thursday = result;
                break;
            case FRIDAY:
                friday = result;
                break;
            case SATURDAY:
                saturday = result;
                break;
            case SUNDAY:
                sunday = result;
                break;
            default:
                break;
        }
    }

    public boolean allCheckedDatesAreValid() {
        return (monday == null || monday.isValid) &&
            (tuesday == null || tuesday.isValid) &&
            (wednesday == null || wednesday.isValid) &&
            (thursday == null || thursday.isValid) &&
            (friday == null || friday.isValid) &&
            (saturday == null || saturday.isValid) &&
            (sunday == null || sunday.isValid);
    }

    public static class ItineraryExistenceResult {
        /**
         * True if an itinerary is available for the applicable day of the week, false otherwise.
         */
        public boolean isValid = true;
        /**
         * Holds a list of invalid dates an itinerary is not available for the applicable day of the week.
         * TODO: This field is for future use.
         */
        public Set<String> invalidDates = new HashSet<>();

        /**
         * Holds a matching itinerary for the applicable day of the week.
         */
        public Itinerary itinerary;

        /**
         * Marks an itinerary as not available for the specified date for the applicable day of the week.
         */
        public void handleInvalidDate (ZonedDateTime date) {
            isValid = false;
            String dateString = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
            invalidDates.add(dateString);
        }
    }
}
