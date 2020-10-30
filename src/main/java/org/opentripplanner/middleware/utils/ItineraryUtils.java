package org.opentripplanner.middleware.utils;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.TripPlan;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public static Map<ZonedDateTime, String> getQueriesFromDates(Map<String, String> params, Set<ZonedDateTime> dates) {
        // Create a copy of the original params in which we change the date.
        Map<String, String> paramsCopy = new HashMap<>(params);
        Map<ZonedDateTime, String> queryParamsByDate = new HashMap<>();

        for (ZonedDateTime date : dates) {
            String dateString = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
            paramsCopy.put(DATE_PARAM, dateString);
            queryParamsByDate.put(date, toQueryString(paramsCopy));
        }

        return queryParamsByDate;
    }

    /**
     * Obtains the monitored dates for the given trip, for which we should check that itineraries exist.
     * The dates include each day to be monitored in the {@link #ITINERARY_CHECK_WINDOW} starting from the trip's query
     * start date.
     * @param trip The trip from which to extract the monitored dates to check.
     * @return A list of date strings in YYYY-MM-DD format corresponding to each day of the week to monitor, sorted from earliest.
     */
    public static Set<ZonedDateTime> getDatesToCheckItineraryExistence(MonitoredTrip trip, boolean checkAllDays)
        throws URISyntaxException {
        Set<ZonedDateTime> datesToCheck = new HashSet<>();
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
     * Gets OTP queries to check non-realtime itinerary existence for the given trip.
     */
    public static Map<ZonedDateTime, String> getItineraryExistenceQueries(MonitoredTrip trip, boolean checkAllDays)
        throws URISyntaxException {
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
     * Checks that, for each query provided, an itinerary exists.
     * @param trip The trip which itinerary is to be checked/matched.
     * @param checkAllDays Determines whether all days of the week are checked,
     *                     or just the days the trip is set to be monitored.
     * @param sortDates Determines whether the dates should be sorted prior to making the OTP requests.
     *                  This is used for tests to to ensure that the order of the dates to check
     *                  matches the order of the mock OTP responses for those dates.
     * @return An object with a map of results and summary of itinerary existence.
     */
    public static ItineraryExistence checkItineraryExistence(MonitoredTrip trip, boolean checkAllDays, boolean sortDates) throws URISyntaxException {
        // Get queries to execute by date.
        Map<ZonedDateTime, String> queriesByDate = ItineraryUtils.getItineraryExistenceQueries(trip, checkAllDays);
        // TODO: Consider multi-threading?
        Map<ZonedDateTime, Itinerary> datesWithMatchingItineraries = new HashMap<>();

        Collection<Map.Entry<ZonedDateTime, String>> entriesToIterate = sortDates
            ? queriesByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList())
            : queriesByDate.entrySet();

        for (Map.Entry<ZonedDateTime, String> entry : entriesToIterate) {
            ZonedDateTime date = entry.getKey();
            String params = entry.getValue();

            OtpDispatcherResponse response = OtpDispatcher.sendOtpPlanRequest(params);
            TripPlan plan = response.getResponse().plan;
            if (plan != null && plan.itineraries != null) {
                for (Itinerary itinerary: plan.itineraries) {
                    // If a matching itinerary is found, save the date with the matching itinerary.
                    // The matching itinerary will replace the original trip.itinerary.
                    // FIXME Replace 'equals' with matching itinerary
                    if (itinerary.equals(trip.itinerary)) {
                        datesWithMatchingItineraries.put(date, itinerary);
                    }
                }
            }
        }
        return new ItineraryExistence(queriesByDate.keySet(), datesWithMatchingItineraries);
    }

    /**
     * Overload used in normal conditions (hides the sort parameter).
     */
    public static ItineraryExistence checkItineraryExistence(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        return checkItineraryExistence(trip, checkAllDays, false);
    }
}
