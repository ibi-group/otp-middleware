package org.opentripplanner.middleware.utils;

import com.spatial4j.core.distance.DistanceUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.TripPlan;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * Overload used in normal conditions.
     */
    public static ItineraryExistence checkItineraryExistence(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        return checkItineraryExistence(trip, checkAllDays,false);
    }

    /**
     * TODO: fully implement
     */
    public static boolean itineraryIsSavable(Itinerary itinerary) {
        return true;
    }

    /**
     * Returns true if the itineraries match for the purposes of trip monitoring.
     *
     * @param previousItinerary The original itinerary that others are compared against.
     * @param newItinerary A new itinerary that might match the previous itinerary.
     */
    public static boolean itinerariesMatch(Itinerary previousItinerary, Itinerary newItinerary) {
        // make sure either itinerary is savable before continuing
        if (!itineraryIsSavable(previousItinerary) || !itineraryIsSavable(newItinerary)) return false;

        // make sure itineraries have same amount of legs
        if (previousItinerary.legs.size() != newItinerary.legs.size()) return false;

        // make sure each leg matches
        for (int i = 0; i < previousItinerary.legs.size(); i++) {
            Leg previousItineraryLeg = previousItinerary.legs.get(i);
            Leg newItineraryLeg = newItinerary.legs.get(i);

            // for now don't analyze non-transit legs
            if (!previousItineraryLeg.transitLeg) continue;

            // make sure the same from/to stop are being used
            if (
                !stopsMatch(previousItineraryLeg.from, newItineraryLeg.from) ||
                    !stopsMatch(previousItineraryLeg.to, newItineraryLeg.to)
            ) {
                return false;
            }

            // make sure the transit service is the same as perceived by the customer
            if (
                !equalsOrPreviousWasNull(previousItineraryLeg.mode, newItineraryLeg.mode) ||
                    !equalsIgnoreCaseOrPreviousWasEmpty(previousItineraryLeg.agencyName, newItineraryLeg.agencyName) ||
                    !equalsIgnoreCaseOrPreviousWasEmpty(
                        previousItineraryLeg.routeLongName,
                        newItineraryLeg.routeLongName
                    ) ||
                    !equalsIgnoreCaseOrPreviousWasEmpty(
                        previousItineraryLeg.routeShortName,
                        newItineraryLeg.routeShortName
                    ) ||
                    !equalsIgnoreCaseOrPreviousWasEmpty(
                        previousItineraryLeg.headsign,
                        newItineraryLeg.headsign
                    ) ||
                    !equalsOrPreviousWasNull(
                        previousItineraryLeg.interlineWithPreviousLeg,
                        newItineraryLeg.interlineWithPreviousLeg
                    )
            ) {
                return false;
            }

            // make sure the transit trips are scheduled for the same time of the day
            if (
                !timeOfDayMatches(
                    previousItineraryLeg.getScheduledStartTime(),
                    newItineraryLeg.getScheduledStartTime()
                ) || !timeOfDayMatches(
                    previousItineraryLeg.getScheduledEndTime(),
                    newItineraryLeg.getScheduledEndTime()
                )
            ) {
                return false;
            }
        }

        // if this point is reached, the itineraries are assumed to match
        return true;
    }

    /**
     * Checks whether two stops (OTP Places) match for the purposes of matching itineraries
     */
    private static boolean stopsMatch(Place stopA, Place stopB) {
        // stop names must match
        if (!StringUtils.equalsIgnoreCase(stopA.name, stopB.name)) return false;

        // stop code must match
        if (!equalsIgnoreCaseOrPreviousWasEmpty(stopA.stopCode, stopB.stopCode)) return false;

        // stop positions must be no further than 5 meters apart
        if (
            DistanceUtils.radians2Dist(
                DistanceUtils.distHaversineRAD(stopA.lat, stopA.lon, stopB.lat, stopB.lon),
                DistanceUtils.EARTH_MEAN_RADIUS_KM
            ) * 1000 > 5
        ) {
            return false;
        };

        // if this point is reached, the stops are assumed to match
        return true;
    }

    /**
     * Returns true if previous is null. Otherwise, returns Objects.equals.
     */
    private static boolean equalsOrPreviousWasNull (Object previous, Object newer) {
        return previous == null || Objects.equals(previous, newer);
    }

    /**
     * Returns true if the previous string was empty. Otherwise, returns if the strings are equal ignoring case.
     */
    private static boolean equalsIgnoreCaseOrPreviousWasEmpty(String previous, String newer) {
        return StringUtils.isEmpty(previous) || StringUtils.equalsIgnoreCase(previous, newer);
    }

    /**
     * Returns true if both times have the same hour, minute and second.
     */
    private static boolean timeOfDayMatches(ZonedDateTime zonedDateTimeA, ZonedDateTime zonedDateTimeB) {
        return zonedDateTimeA.getHour() == zonedDateTimeB.getHour() &&
            zonedDateTimeA.getMinute() == zonedDateTimeB.getMinute() &&
            zonedDateTimeA.getSecond() == zonedDateTimeB.getSecond();
    }

    /**
     * Variant of checkItineraryExistence used for tests,
     * to ensure that the order of the dates to check matches the order of the mock OTP responses for those dates.
     */
    public static ItineraryExistence checkItineraryExistenceOrdered(MonitoredTrip trip, boolean checkAllDays) throws URISyntaxException {
        return checkItineraryExistence(trip, checkAllDays,true);
    }

}
