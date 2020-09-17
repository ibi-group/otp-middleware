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
import java.util.Optional;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getZoneIdForCoordinates;

public class OtpQueryUtils {
    public static Map<String, String> getQueryParams(String queryParams) throws URISyntaxException {
        List<NameValuePair> params = URLEncodedUtils.parse(
            new URI(String.format("http://example.com/%s", queryParams)),
            UTF_8
        );
        return params.stream().collect(Collectors.toMap(NameValuePair::getName,NameValuePair::getValue));
    }

    public static List<NameValuePair> toNameValuePairs(Map<String, String> params) {
        return params.entrySet().stream()
            .map(e -> new BasicNameValuePair(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * Creates a new query string based on the one provided, with the date changed to the desired one.
     * @param query the base query.
     * @param newDates the desired dates.
     * @return a list of query strings with one of the specified dates.
     */
    public static List<String> makeQueryStringsWithNewDates(String query, List<String> newDates) throws URISyntaxException {
        List<String> result = new ArrayList<>();
        Map<String, String> params = getQueryParams(query);

        for (String newDate : newDates) {
            params.put("date", newDate);
            result.add(URLEncodedUtils.format(toNameValuePairs(params), UTF_8));
        }

        return result;
    }

    /**
     * Obtains the days for checking trip existence, for each day the trip is set to be monitored,
     * starting from the day of the query params up to 7 days.
     * @param trip the {@link MonitoredTrip} to get the date for.
     * @return a list of date strings in YYYY-MM-DD format corresponding to each day of the week to monitor.
     */
    public static List<String> getDatesForCheckingTripExistence(MonitoredTrip trip) throws URISyntaxException {
        List<String> result = new ArrayList<>();

        // FIXME: Refactor this if block (Same as in CheckMonitoredTrip#shouldSkipMonitoredTripCheck)
        ZoneId zoneId;
        Optional<ZoneId> fromZoneId = getZoneIdForCoordinates(trip.from.lat, trip.from.lon);
        if (fromZoneId.isEmpty()) {
            String message = String.format(
                "Could not find coordinate's (lat=%.6f, lon=%.6f) timezone for monitored trip %s",
                trip.from.lat,
                trip.from.lon,
                trip.id
            );
            throw new RuntimeException(message);
        } else {
            zoneId = fromZoneId.get();
        }

        // Start from the query date, if available.
        // If not, start from today.
        Map<String, String> queryParams = getQueryParams(trip.queryParams);
        String queryDateString = queryParams.getOrDefault("date", DateTimeUtils.getStringFromDate(DateTimeUtils.nowAsLocalDate(), DateTimeUtils.YYYY_MM_DD));
        LocalDate queryDate = DateTimeUtils.getDateFromString(queryDateString, DateTimeUtils.YYYY_MM_DD);

        // Get current time and trip time (with the time offset to today) for comparison.
        // FIXME: replace this block with code from PR #75.
        String[] tripTimeSplit = queryParams.get("time").split(":");
        int tripHour = Integer.parseInt(tripTimeSplit[0]);
        int tripMinutes = Integer.parseInt(tripTimeSplit[1]);
        ZonedDateTime queryZonedDateTime = ZonedDateTime.of(queryDate, LocalTime.of(tripHour, tripMinutes), zoneId);


        // Check the dates starting from the query date, going through 7 days.
        for (int i = 0; i < 7; i++) {
            ZonedDateTime probedDate = queryZonedDateTime.plusDays(i);
            if (trip.isActiveOnDate(probedDate)) {
                result.add(DateTimeUtils.getStringFromDate(probedDate.toLocalDate(), DateTimeUtils.YYYY_MM_DD));
            }
        }

        return result;
    }
}
