package org.opentripplanner.middleware.utils;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.MonitoredTrip;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A utility class for dealing with OTP queries.
 */
public class OtpQueryUtils {
    /**
     * Converts query parameters to a {@link Map}.
     */
    public static Map<String, String> getQueryParams(String queryParams) throws URISyntaxException {
        List<NameValuePair> params = URLEncodedUtils.parse(
            new URI(String.format("http://example.com/%s", queryParams)),
            UTF_8
        );
        return params.stream().collect(Collectors.toMap(NameValuePair::getName,NameValuePair::getValue));
    }

    /**
     * Converts a parameter {@link Map} to a list of {@link NameValuePair} entries.
     */
    public static List<NameValuePair> toNameValuePairs(Map<String, String> params) {
        return params.entrySet().stream()
            .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * Creates a new query string based on the one provided, with the date changed to the desired one.
     * @param baseQuery the base OTP query.
     * @param dates a list of the desired dates in YYYY-MM-DD format.
     * @return a list of query strings with one of the specified dates.
     */
    public static List<String> queriesFromDates(String baseQuery, List<String> dates) throws URISyntaxException {
        List<String> result = new ArrayList<>();
        Map<String, String> params = getQueryParams(baseQuery);

        for (String newDate : dates) {
            params.put("date", newDate);
            result.add(URLEncodedUtils.format(toNameValuePairs(params), UTF_8));
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
        Map<String, String> queryParams = getQueryParams(trip.queryParams);

        // Start from the query date, if available.
        // If there is no query date, start from today.
        String queryDateString = queryParams.getOrDefault("date", DateTimeUtils.getStringFromDate(DateTimeUtils.nowAsLocalDate(), DateTimeUtils.YYYY_MM_DD));
        LocalDate queryDate = DateTimeUtils.getDateFromString(queryDateString, DateTimeUtils.YYYY_MM_DD);

        // Get current time and trip time (with the time offset to today) for comparison.
        // FIXME: replace this block with code from PR #75.
        String[] tripTimeSplit = queryParams.get("time").split(":");
        int tripHour = Integer.parseInt(tripTimeSplit[0]);
        int tripMinutes = Integer.parseInt(tripTimeSplit[1]);
        ZonedDateTime queryZonedDateTime = ZonedDateTime.of(queryDate, LocalTime.of(tripHour, tripMinutes), zoneId);


        // Check the dates in a 7-day window starting from the query date.
        for (int i = 0; i < 7; i++) {
            ZonedDateTime probedDate = queryZonedDateTime.plusDays(i);
            if (trip.isActiveOnDate(probedDate)) {
                result.add(DateTimeUtils.getStringFromDate(probedDate.toLocalDate(), DateTimeUtils.YYYY_MM_DD));
            }
        }

        return result;
    }

    /**
     * Gets OTP queries to check itinerary existence for the given trip.
     */
    public static List<String> getItineraryExistenceQueries(MonitoredTrip trip) throws URISyntaxException {
        return queriesFromDates(trip.queryParams, getDatesToCheckItineraryExistence(trip));
    }
}
