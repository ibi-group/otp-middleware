package org.opentripplanner.middleware.utils;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Itinerary;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;

/**
 * A utility class for dealing with OTP queries and itineraries.
 */
public class ItineraryUtils {

    public static final String IGNORE_REALTIME_UPDATES_PARAM = "ignoreRealtimeUpdates";
    public static final String DATE_PARAM = "date";
    public static final String TIME_PARAM = "time";

    /**
     * Converts a {@link Map} to a URL query string (does not include a leading '?').
     */
    public static String toQueryString(Map<String, String> params) {
        List<BasicNameValuePair> nameValuePairs = params.entrySet().stream()
            .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
        return URLEncodedUtils.format(nameValuePairs, UTF_8);
    }

    /**
     * Creates a map of new query strings based on the one provided,
     * with the date changed to the desired one.
     * @param params a map of the base OTP query parameters.
     * @param dates a list of the desired dates in YYYY-MM-DD format.
     * @return a map of query strings with, and indexed by the specified dates.
     */
    public static Map<String, String> getQueriesFromDates(Map<String, String> params, List<String> dates) {
        // Create a copy of the original params in which we change the date.
        Map<String, String> paramsCopy = new HashMap<>(params);
        Map<String, String> queryParamsByDate = new HashMap<>();

        for (String date : dates) {
            paramsCopy.put(DATE_PARAM, date);
            queryParamsByDate.put(date, toQueryString(paramsCopy));
        }

        return queryParamsByDate;
    }

    /**
     * Obtains the monitored dates for the given trip, for which we should check that itineraries exist.
     * The dates include each day to be monitored in a 7-day window starting from the trip's query start date.
     * @param trip The trip from which to extract the monitored dates to check.
     * @param checkAllDays true if all days of the week are included regardless of the trip's monitored days.
     * @return A list of date strings in YYYY-MM-DD format corresponding to each day of the week to monitor.
     */
    public static List<String> getDatesToCheckItineraryExistence(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        List<String> result = new ArrayList<>();
        ZoneId zoneId = trip.timezoneForTargetLocation();
        Map<String, String> params = trip.parseQueryParams();

        // Start from the query date, if available.
        // If there is no query date, start from today.
        String queryDateString = params.getOrDefault(DATE_PARAM, DateTimeUtils.getStringFromDate(DateTimeUtils.nowAsLocalDate(), DEFAULT_DATE_FORMAT_PATTERN));
        LocalDate queryDate = DateTimeUtils.getDateFromString(queryDateString, DEFAULT_DATE_FORMAT_PATTERN);

        // Get the trip time.
        ZonedDateTime queryZonedDateTime = ZonedDateTime.of(queryDate, LocalTime.of(trip.tripTimeHour(), trip.tripTimeMinute()), zoneId);

        // Check the dates on days when a trip is active, or every day if checkAllDays is true,
        // in a 7-day window starting from the query date.
        for (int i = 0; i < 7; i++) {
            ZonedDateTime probedDate = queryZonedDateTime.plusDays(i);
            if (checkAllDays || trip.isActiveOnDate(probedDate)) {
                result.add(DateTimeUtils.getStringFromDate(probedDate.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN));
            }
        }

        return result;
    }

    /**
     * Gets OTP queries to check non-realtime itinerary existence for the given trip.
     */
    public static Map<String, String> getItineraryExistenceQueries(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        return getQueriesFromDates(
            excludeRealtime(trip.parseQueryParams()),
            getDatesToCheckItineraryExistence(trip, checkAllDays)
        );
    }

    /**
     * @return a copy of the specified query parameter map, with ignoreRealtimeUpdates set to true.
     */
    public static Map<String, String> excludeRealtime(Map<String, String> params) {
        Map<String, String> result = new HashMap<>(params);
        result.put(IGNORE_REALTIME_UPDATES_PARAM, "true");
        return result;
    }

    /**
     * Replaces the trip itinerary field value with one from the verified itineraries queried from OTP.
     * The ui_activeItinerary query parameter is used to determine which itinerary to use,
     * TODO/FIXME: need a trip resemblance check to supplement the ui_activeItinerary param used in this function.
     */
    public static void updateTripWithVerifiedItinerary(MonitoredTrip trip, List<Itinerary> verifiedItineraries) throws URISyntaxException {
        Map<String, String> params = trip.parseQueryParams();
        Itinerary itinerary = null;
        String itineraryIndexParam = params.get("ui_activeItinerary");
        if (itineraryIndexParam != null) {
            try {
                int itineraryIndex = Integer.parseInt(itineraryIndexParam);
                itinerary = verifiedItineraries.get(itineraryIndex);
            } catch (NumberFormatException e) {
                // TODO: error message.
            }
        } else {
            // FIXME: implement itinerary resemblance to find one matching trip.itinerary.
        }

        if (itinerary != null) {
            trip.itinerary = itinerary;
            trip.initializeFromItineraryAndQueryParams();
        }
    }

    /**
     * Checks that the specified itinerary is on the same day as the specified date/time.
     * @param itinerary the itinerary to check.
     * @param date the request date to check.
     * @param time the request time to check.
     * @param tripIsArriveBy true to check the itinerary endtime, false to check the startTime.
     * @return true if the itinerary's startTime or endTime is one the same day as the day of the specified date and time.
     */
    public static boolean isSameDay(Itinerary itinerary, String date, String time, ZoneId zoneId, boolean tripIsArriveBy) {
        // TODO: Make SERVICE_DAY_START_HOUR an optional config parameter.
        final int SERVICE_DAY_START_HOUR = 3;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT_PATTERN);

        Date itineraryTime = tripIsArriveBy ? itinerary.endTime : itinerary.startTime;
        ZonedDateTime startDate = ZonedDateTime.ofInstant(itineraryTime.toInstant(), zoneId);

        // If the OTP request was made at a time before SERVICE_DAY_START_HOUR
        // (for instance, a request with a departure or arrival at 12:30 am),
        // then consider the request to have been made the day before.
        // To compensate, advance startDate by one day.
        String hour = time.split(":")[0];
        if (Integer.parseInt(hour) < SERVICE_DAY_START_HOUR) {
            startDate = startDate.plusDays(1);
        }

        ZonedDateTime startDateDayBefore = startDate.minusDays(1);

        return (
            date.equals(startDate.format(dateFormatter)) &&
                startDate.getHour() >= SERVICE_DAY_START_HOUR
        ) || (
            // Trips starting between 12am and 2:59am next day are considered same-day.
            date.equals(startDateDayBefore.format(dateFormatter)) &&
                startDateDayBefore.getHour() < SERVICE_DAY_START_HOUR
        );
    }

    public static List<Itinerary> getSameDayItineraries(List<Itinerary> itineraries, MonitoredTrip trip, String date) throws URISyntaxException {
        List<Itinerary> result = new ArrayList<>();

        if (itineraries != null) {
            for (Itinerary itinerary : itineraries) {
                // TODO/FIXME: initialize the parameter map in the monitored trip in initializeFromItinerary etc.
                // so we don't have to lug the URI exception around.
                if (isSameDay(itinerary, date, trip.tripTime, trip.timezoneForTargetLocation(), trip.isArriveBy())) {
                    result.add(itinerary);
                }
            }
        }

        return result;
    }
}
