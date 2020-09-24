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
    public static final String TIME_PARAM = "time";

    /**
     * Converts query strings that start with '?' to a {@link Map} of keys and values.
     */
    public static Map<String, String> getQueryParams(String queryParams) throws URISyntaxException {
        List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(
            new URI(String.format("http://example.com/%s", queryParams)),
            UTF_8
        );
        return nameValuePairs.stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    }

    /**
     * Converts a {@link Map} to a URL query string, with or without a leading '?'.
     */
    public static String toQueryString(Map<String, String> params, boolean leadingQuestionMark) {
        List<BasicNameValuePair> nameValuePairs = params.entrySet().stream()
            .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
        // FIXME: Upon merging #75, do not add '?' and remove the question mark option entirely everywhere.
        return (leadingQuestionMark ? "?" : "") +
            URLEncodedUtils.format(nameValuePairs, UTF_8);
    }

    // FIXME: Upon merging #75, remove this overload.
    public static String toQueryString(Map<String, String> params) {
        return toQueryString(params, false);
    }

    /**
     * Creates a map of new query strings based on the one provided,
     * with the date changed to the desired one and ignoring realtime updates.
     *
     * @param baseQueryParams the base OTP query.
     * @param dates           a list of the desired dates in YYYY-MM-DD format.
     * @return a map of query strings with, and indexed by the specified dates.
     */
    public static Map<String, String> getQueriesFromDates(String baseQueryParams, List<String> dates) throws URISyntaxException {
        Map<String, String> result = new HashMap<>();
        Map<String, String> params = getQueryParams(baseQueryParams);

        for (String date : dates) {
            params.put(DATE_PARAM, date);
            result.put(date, toQueryString(params));
        }

        return result;
    }

    /**
     * Obtains dates for which we should check that itineraries exist for the specified trip.
     * The dates include each day to be monitored in a 7-day window starting from the trip's query start date.
     *
     * @return a list of date strings in YYYY-MM-DD format corresponding to each day of the week to monitor.
     */
    public static List<String> getDatesToCheckItineraryExistence(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        List<String> result = new ArrayList<>();
        ZoneId zoneId = trip.timezoneForTargetLocation();
        Map<String, String> params = getQueryParams(trip.queryParams);

        // Start from the query date, if available.
        // If there is no query date, start from today.
        String queryDateString = params.getOrDefault(DATE_PARAM, DateTimeUtils.getStringFromDate(DateTimeUtils.nowAsLocalDate(), YYYY_MM_DD));
        LocalDate queryDate = DateTimeUtils.getDateFromString(queryDateString, YYYY_MM_DD);

        // Get the trip time.
        // FIXME: replace this block with code from PR #75.
        String[] tripTimeSplit = params.get("time").split(":");
        int tripHour = Integer.parseInt(tripTimeSplit[0]);
        int tripMinutes = Integer.parseInt(tripTimeSplit[1]);
        ZonedDateTime queryZonedDateTime = ZonedDateTime.of(queryDate, LocalTime.of(tripHour, tripMinutes), zoneId);


        // Check the dates on days when a trip is active, or every day if checkAllDays is true,
        // in a 7-day window starting from the query date.
        for (int i = 0; i < 7; i++) {
            ZonedDateTime probedDate = queryZonedDateTime.plusDays(i);
            if (checkAllDays || trip.isActiveOnDate(probedDate)) {
                result.add(DateTimeUtils.getStringFromDate(probedDate.toLocalDate(), YYYY_MM_DD));
            }
        }

        return result;
    }

    /**
     * Gets OTP queries to check non-realtime itinerary existence for the given trip.
     */
    public static Map<String, String> getItineraryExistenceQueries(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        return getQueriesFromDates(
            excludeRealtime(trip.queryParams),
            getDatesToCheckItineraryExistence(trip, checkAllDays)
        );
    }

    /**
     * @return a copy of the specified query, with ignoreRealtimeUpdates set to true.
     */
    public static String excludeRealtime(String queryParams) throws URISyntaxException {
        Map<String, String> params = getQueryParams(queryParams);
        params.put(IGNORE_REALTIME_UPDATES_PARAM, "true");

        // Insert '?' so others can parse the resulting query string.
        return toQueryString(params, true);
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
            trip.initializeFromItineraryAndQueryParams();
        }
    }

    /**
     * Checks that the specified itinerary is on the same day as the specified date/time.
     * @param itinerary the itinerary to check.
     * @param date the request date to check.
     * @param time the request time to check.
     * @param checkArrival true to check the itinerary endtime, false to check the startTime.
     * @return true if the itinerary's startTime or endTime is one the same day as the day of the specified date and time.
     */
    public static boolean isSameDay(Itinerary itinerary, String date, String time, ZoneId zoneId, boolean checkArrival) {
        // TODO: Make SERVICEDAY_START_HOUR an optional config parameter.
        final int SERVICEDAY_START_HOUR = 3;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(YYYY_MM_DD);

        ZonedDateTime startDate = ZonedDateTime.ofInstant(itinerary.getStartOrEndTime(checkArrival).toInstant(), zoneId);

        // If the OTP request was made at a time before SERVICEDAY_START_HOUR
        // (for instance, a request with a departure or arrival at 12:30 am),
        // then consider the request to have been made the day before.
        // To compensate, advance startDate by one day.
        String hour = time.split(":")[0];
        if (Integer.parseInt(hour) < SERVICEDAY_START_HOUR) {
            startDate = startDate.plusDays(1);
        }

        ZonedDateTime startDateDayBefore = startDate.minusDays(1);

        return (
            date.equals(startDate.format(dateFormatter)) &&
                startDate.getHour() >= SERVICEDAY_START_HOUR
        ) || (
            // Trips starting between 12am and 2:59am next day are considered same-day.
            date.equals(startDateDayBefore.format(dateFormatter)) &&
                startDateDayBefore.getHour() < SERVICEDAY_START_HOUR
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
