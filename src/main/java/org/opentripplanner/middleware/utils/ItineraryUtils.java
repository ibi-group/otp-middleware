package org.opentripplanner.middleware.utils;

import com.spatial4j.core.distance.DistanceUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;

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
import java.util.Objects;
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
    public static List<String> getDatesToCheckItineraryExistence(MonitoredTrip trip, boolean checkAllDays)
        throws URISyntaxException {
        List<String> result = new ArrayList<>();
        ZoneId zoneId = DateTimeUtils.getOtpZoneId();
        Map<String, String> params = trip.parseQueryParams();

        // Start from the query date, if available.
        // If there is no query date, start from today.
        String queryDateString = params.getOrDefault(
            DATE_PARAM, DateTimeUtils.getStringFromDate(DateTimeUtils.nowAsLocalDate(), DEFAULT_DATE_FORMAT_PATTERN)
        );
        LocalDate queryDate = DateTimeUtils.getDateFromString(queryDateString, DEFAULT_DATE_FORMAT_PATTERN);

        // Get the trip time.
        ZonedDateTime queryZonedDateTime = ZonedDateTime.of(
            queryDate, LocalTime.of(trip.tripTimeHour(), trip.tripTimeMinute()), zoneId
        );

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
    public static Map<String, String> getItineraryExistenceQueries(MonitoredTrip trip, boolean checkAllDays)
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
     * Replaces the trip itinerary field value with one from the verified itineraries queried from OTP.
     * The ui_activeItinerary query parameter is used to determine which itinerary to use,
     * TODO/FIXME: need a trip resemblance check to supplement the ui_activeItinerary param used in this function.
     */
    public static void updateTripWithVerifiedItinerary(MonitoredTrip trip, List<Itinerary> verifiedItineraries)
        throws URISyntaxException {
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

    public static List<Itinerary> getSameDayItineraries(List<Itinerary> itineraries, MonitoredTrip trip, String date)
        throws URISyntaxException {
        List<Itinerary> result = new ArrayList<>();

        if (itineraries != null) {
            for (Itinerary itinerary : itineraries) {
                // TODO/FIXME: initialize the parameter map in the monitored trip in initializeFromItinerary etc.
                //   so we don't have to lug the URI exception around.
                if (isSameDay(itinerary, date, trip.tripTime, DateTimeUtils.getOtpZoneId(), trip.isArriveBy())) {
                    result.add(itinerary);
                }
            }
        }

        return result;
    }

    /**
     * Checks that, for each query provided, an itinerary exists.
     * @param labeledQueries a map containing the queries to check, each query having a key or
     *                       label that will be used in Result.labeledResponses for easy identification.
     * @return An object with a map of results and summary of itinerary existence.
     */
    public static Result checkItineraryExistence(Map<String, String> labeledQueries, boolean tripIsArriveBy) {
        // TODO: Consider multi-threading?
        Map<String, OtpResponse> responses = new HashMap<>();
        boolean allItinerariesExist = true;

        for (Map.Entry<String, String> entry : labeledQueries.entrySet()) {
            OtpDispatcherResponse response = OtpDispatcher.sendOtpPlanRequest(entry.getValue());
            responses.put(entry.getKey(), response.getResponse());

            Itinerary sameDayItinerary = response.findItineraryDepartingSameDay(tripIsArriveBy);
            if (sameDayItinerary == null) allItinerariesExist = false;
        }

        return new Result(allItinerariesExist, responses);
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
     * Class to pass the results of the OTP itinerary checks.
     */
    public static class Result {
        /** Whether all itineraries checked exist. */
        public final boolean allItinerariesExist;
        /**
         * A map with the same keys as the input from checkAll,
         * and values as OTP responses for the corresponding queries.
         */
        public final Map<String, OtpResponse> labeledResponses;

        private Result(boolean itinerariesExist, Map<String, OtpResponse> labeledResponses) {
            this.allItinerariesExist = itinerariesExist;
            this.labeledResponses = labeledResponses;
        }
    }
}
