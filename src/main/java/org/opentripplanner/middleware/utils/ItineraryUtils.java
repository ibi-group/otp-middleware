package org.opentripplanner.middleware.utils;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Itinerary;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.utils.DateTimeUtils.YYYY_MM_DD;

/**
 * A utility class for dealing with OTP queries and itineraries.
 */
public class ItineraryUtils {

    public static final String IGNORE_REALTIME_UPDATES_PARAM = "ignoreRealtimeUpdates";
    public static final String DATE_PARAM = "date";

    /**
     * Converts query parameters that starts with '?' to a {@link Map}.
     */
    public static Map<String, String> getQueryParams(String queryParams) throws URISyntaxException {
        List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(
            new URI(String.format("http://example.com/%s", queryParams)),
            UTF_8
        );
        return nameValuePairs.stream().collect(Collectors.toMap(NameValuePair::getName,NameValuePair::getValue));
    }

    /**
     * Converts a {@link Map} to a URL query string (not including '?').
     */
    public static String toQueryString(Map<String, String> params) {
        List<BasicNameValuePair> nameValuePairs = params.entrySet().stream()
            .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
        return URLEncodedUtils.format(nameValuePairs, UTF_8);
    }

    /**
     * Creates a map of new query strings based on the one provided,
     * with the date changed to the desired one and ignoring realtime updates.
     * @param baseQueryParams the base OTP query.
     * @param dates a list of the desired dates in YYYY-MM-DD format.
     * @return a map of query strings with, and indexed by the specified dates.
     */
    public static Map<String, String> getQueriesFromDates(String baseQueryParams, List<String> dates) throws URISyntaxException {
        Map<String, String> result = new HashMap<>();
        Map<String, String> params = getQueryParams(baseQueryParams);

        for (String newDate : dates) {
            params.put(DATE_PARAM, newDate);
            result.put(newDate, toQueryString(params));
        }

        return result;
    }

    /**
     * Obtains dates for which we should check that itineraries exist for the specified trip.
     * The dates include each day is set to be monitored in a 7-day window starting from the query start date.
     * @param trip the {@link MonitoredTrip} to get the date for.
     * @return a list of date strings in YYYY-MM-DD format corresponding to each day of the week to monitor.
     */
    public static List<String> getDatesToCheckItineraryExistence(MonitoredTrip trip) throws URISyntaxException {
        List<String> result = new ArrayList<>();
        ZoneId zoneId = trip.tripZoneId();
        Map<String, String> params = getQueryParams(trip.queryParams);

        // Start from the query date, if available.
        // If there is no query date, start from today.
        String queryDateString = params.getOrDefault(DATE_PARAM, DateTimeUtils.getStringFromDate(DateTimeUtils.nowAsLocalDate(), YYYY_MM_DD));
        LocalDate queryDate = DateTimeUtils.getDateFromString(queryDateString, YYYY_MM_DD);

        // Get current time and trip time (with the time offset to today) for comparison.
        // FIXME: replace this block with code from PR #75.
        String[] tripTimeSplit = params.get("time").split(":");
        int tripHour = Integer.parseInt(tripTimeSplit[0]);
        int tripMinutes = Integer.parseInt(tripTimeSplit[1]);
        ZonedDateTime queryZonedDateTime = ZonedDateTime.of(queryDate, LocalTime.of(tripHour, tripMinutes), zoneId);


        // Check the dates in a 7-day window starting from the query date.
        for (int i = 0; i < 7; i++) {
            ZonedDateTime probedDate = queryZonedDateTime.plusDays(i);
            if (trip.isActiveOnDate(probedDate)) {
                result.add(DateTimeUtils.getStringFromDate(probedDate.toLocalDate(), YYYY_MM_DD));
            }
        }

        return result;
    }

    /**
     * Gets OTP queries to check non-realtime itinerary existence for the given trip.
     */
    public static Map<String, String> getItineraryExistenceQueries(MonitoredTrip trip) throws URISyntaxException {
        return getQueriesFromDates(
            excludeRealtime(trip.queryParams),
            getDatesToCheckItineraryExistence(trip)
        );
    }

    /**
     * Sets the ignoreRealtimeUpdates parameter to true.
     * @param queryParams the query to modify.
     * @return the modified query.
     */
    public static String excludeRealtime(String queryParams) throws URISyntaxException {
        Map<String, String> params = getQueryParams(queryParams);
        params.put(IGNORE_REALTIME_UPDATES_PARAM, "true");

        // Insert '?' so others can parse the resulting query string.
        return "?" + toQueryString(params);
    }

    /**
     * Replaces the trip itinerary field value with one from the verified itineraries queried from OTP.
     * The ui_activeItinerary query parameter is used to determine which itinerary to use,
     * TODO/FIXME: need a trip resemblance check to supplement the ui_activeItinerary param used in this function.
     */
    public static void updateTripWithVerifiedItinerary(MonitoredTrip trip, List<Itinerary> verifiedItineraries) throws URISyntaxException {
        Map<String, String> params = getQueryParams(trip.queryParams);
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
        }
    }

    /**
     * @return true if the itinerary's startTime is one the same day as the specified date.
     */
    public static boolean itineraryDepartsSameDay(Itinerary itinerary, String date, ZoneId zoneId) {
        ZonedDateTime startDate = ZonedDateTime.ofInstant(itinerary.startTime.toInstant(), zoneId);
        ZonedDateTime startDateDayBefore = startDate.minusDays(1);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(YYYY_MM_DD);
        final int SERVICEDAY_START_HOUR = 3;

        return (
            date.equals(startDate.format(dateFormatter)) &&
                startDate.getHour() >= SERVICEDAY_START_HOUR
        ) || (
            // Trips starting between 12am and 2:59am next day are considered same-day.
            // TODO: Make SERVICEDAY_START_HOUR a config parameter.
            date.equals(startDateDayBefore.format(dateFormatter)) &&
                startDateDayBefore.getHour() < SERVICEDAY_START_HOUR
        );
    }
}
