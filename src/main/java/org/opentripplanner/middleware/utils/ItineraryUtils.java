package org.opentripplanner.middleware.utils;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpRequest;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    public static final int ITINERARY_CHECK_WINDOW = 7;

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
    public static List<OtpRequest> getOtpRequestsForDates(Map<String, String> params, List<ZonedDateTime> dates) {
        // Create a copy of the original params in which we change the date.
        List<OtpRequest> requests = new ArrayList<>();
        for (ZonedDateTime date : dates) {
            // Get updated date string and add to params copy.
            Map<String, String> paramsCopy = new HashMap<>(params);
            String dateString = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
            paramsCopy.put(DATE_PARAM, dateString);
            requests.add(new OtpRequest(date, paramsCopy));
        }
        return requests;
    }

    /**
     * Obtains the monitored dates for the given trip, for which we should check that itineraries exist.
     * The dates include each day to be monitored in the {@link #ITINERARY_CHECK_WINDOW} starting from the trip's query
     * start date.
     * @param trip The trip from which to extract the monitored dates to check.
     * @return A list of date strings in YYYY-MM-DD format corresponding to each day of the week to monitor, sorted from earliest.
     */
    public static List<ZonedDateTime> getDatesToCheckItineraryExistence(MonitoredTrip trip, boolean checkAllDays)
        throws URISyntaxException {
        List<ZonedDateTime> datesToCheck = new ArrayList<>();
        Map<String, String> params = trip.parseQueryParams();

        // Start from the query date, if available.
        String startingDateString = params.get(DATE_PARAM);
        // If there is no query date, start from today.
        LocalDate startingDate = DateTimeUtils.getDateFromQueryDateString(startingDateString);
        ZonedDateTime startingDateTime = trip.tripZonedDateTime(startingDate);
        // Get the dates to check starting from the query date and continuing through the full date range window.
        for (int i = 0; i < ITINERARY_CHECK_WINDOW; i++) {
            ZonedDateTime dateToCheck = startingDateTime.plusDays(i);
            if (checkAllDays || trip.isActiveOnDate(dateToCheck)) {
                datesToCheck.add(dateToCheck);
            }
        }

        return datesToCheck;
    }

    /**
     * @return a copy of the specified query parameter map, with ignoreRealtimeUpdates set to true.
     */
    public static Map<String, String> excludeRealtime(Map<String, String> params) {
        Map<String, String> result = new HashMap<>(params);
        result.put(IGNORE_REALTIME_UPDATES_PARAM, "true");
        return result;
    }
}
