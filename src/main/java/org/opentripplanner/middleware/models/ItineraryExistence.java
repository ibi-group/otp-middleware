package org.opentripplanner.middleware.models;

import com.google.common.collect.Sets;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;

/**
 * This class holds a list of OTP itineraries for each day of the week,
 * so that clients can check that itineraries exist
 * or compare the returned itineraries with another one.
 */
public class ItineraryExistence {
    public ItineraryExistenceResult monday = new ItineraryExistenceResult();
    public ItineraryExistenceResult tuesday = new ItineraryExistenceResult();
    public ItineraryExistenceResult wednesday = new ItineraryExistenceResult();
    public ItineraryExistenceResult thursday = new ItineraryExistenceResult();
    public ItineraryExistenceResult friday = new ItineraryExistenceResult();
    public ItineraryExistenceResult saturday = new ItineraryExistenceResult();
    public ItineraryExistenceResult sunday = new ItineraryExistenceResult();

    public ItineraryExistence(Set<ZonedDateTime> datesChecked, Set<ZonedDateTime> datesWithMatches) {
        Set<ZonedDateTime> invalidDates = Sets.difference(datesChecked, datesWithMatches);
        for (ZonedDateTime date : invalidDates) {
            ItineraryExistenceResult result = getResultForDayOfWeek(date.getDayOfWeek());
            result.handleInvalidDate(date);
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

    public class ItineraryExistenceResult {
        public boolean isValid = true;
        public Set<String> invalidDates = new HashSet<>();
        public void handleInvalidDate (ZonedDateTime date) {
            isValid = false;
            String dateString = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
            invalidDates.add(dateString);
        }
    }
}
