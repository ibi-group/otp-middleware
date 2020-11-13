package org.opentripplanner.middleware.utils;

import com.spatial4j.core.distance.DistanceUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.OtpRequest;

import java.net.URISyntaxException;
import java.time.LocalDate;
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

    /**
     * TODO: fully implement
     */
    public static boolean itineraryIsSavable(Itinerary itinerary) {
        return true;
    }

    /**
     * Returns true if the itineraries match for the purposes of trip monitoring.
     *
     * @param referenceItinerary The original itinerary that others are compared against.
     * @param candidiateItinerary A new itinerary that might match the previous itinerary.
     */
    public static boolean itinerariesMatch(Itinerary referenceItinerary, Itinerary candidiateItinerary) {
        // Make sure both itineraries are monitorable before continuing.
        if (!itineraryIsSavable(referenceItinerary) || !itineraryIsSavable(candidiateItinerary)) return false;

        // make sure itineraries have same amount of legs
        if (referenceItinerary.legs.size() != candidiateItinerary.legs.size()) return false;

        // make sure each leg matches
        for (int i = 0; i < referenceItinerary.legs.size(); i++) {
            Leg referenceItineraryLeg = referenceItinerary.legs.get(i);
            Leg candidateItineraryLeg = candidiateItinerary.legs.get(i);

            if (!legsMatch(referenceItineraryLeg, candidateItineraryLeg)) return false;
        }

        // if this point is reached, the itineraries are assumed to match
        return true;
    }

    /**
     * Checks that the specified itinerary is on the same day as the specified date/time.
     * @param itinerary the itinerary to check.
     * @param requestDateTime the request date/time to check, in the OTP's time zone.
     * @param tripIsArriveBy true to check the itinerary endtime, false to check the startTime.
     * @return true if the itinerary's startTime or endTime is one the same day as the day of the specified date and time.
     */
    public static boolean isSameDay(Itinerary itinerary, ZonedDateTime requestDateTime, boolean tripIsArriveBy) {
        // TODO: Make SERVICE_DAY_START_HOUR an optional config parameter.
        final int SERVICE_DAY_START_HOUR = 3;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT_PATTERN);

        Date itineraryTime = tripIsArriveBy ? itinerary.endTime : itinerary.startTime;
        ZonedDateTime startDateTime = ZonedDateTime.ofInstant(itineraryTime.toInstant(), DateTimeUtils.getOtpZoneId());

        // If the OTP request was made at a time before SERVICE_DAY_START_HOUR
        // (for instance, a request with a departure or arrival at 12:30 am),
        // then consider the request to have been made the day before.
        // To compensate, advance startDate by one day.
        int hour = requestDateTime.toLocalTime().getHour();
        if (hour < SERVICE_DAY_START_HOUR) {
            startDateTime = startDateTime.plusDays(1);
        }

        ZonedDateTime startDateTimeDayBefore = startDateTime.minusDays(1);

        LocalDate requestLocalDate = requestDateTime.toLocalDate();
        return (
            requestLocalDate.equals(startDateTime.toLocalDate()) &&
            startDateTime.getHour() >= SERVICE_DAY_START_HOUR
        ) || (
            // Trips starting between 12am and 2:59am next day are considered same-day.
            requestLocalDate.equals(startDateTimeDayBefore.toLocalDate()) &&
            startDateTimeDayBefore.getHour() < SERVICE_DAY_START_HOUR
        );
    }
    /**
     * Check whether a new leg of an itinerary matches the previous itinerary leg for the purposes of trip monitoring.
     */
    private static boolean legsMatch(Leg referenceItineraryLeg, Leg candidateItineraryLeg) {
        // for now don't analyze non-transit legs
        if (!referenceItineraryLeg.transitLeg) return true;

        // make sure the same from/to stop are being used
        if (
            !stopsMatch(referenceItineraryLeg.from, candidateItineraryLeg.from) ||
                !stopsMatch(referenceItineraryLeg.to, candidateItineraryLeg.to)
        ) {
            return false;
        }

        // Make sure the transit service is the same as perceived by the customer. It is assumed that the transit
        // service is the same expereince to a customer if the following conditions are met:
        // - The modes of transportation are the same
        // - The agency name of the transit service is the same (or the reference leg had an empty agency name)
        // - The route's long name is the same (or the reference leg had an empty route long name)
        // - The route's short name is the same (or the reference leg had an empty route short name)
        // - The headsign is the same (or the reference leg had an empty headsign)
        // - The leg has the same interlining qualities with the previous leg
        if (
            !equalsOrPreviousWasNull(referenceItineraryLeg.mode, candidateItineraryLeg.mode) ||
                !equalsIgnoreCaseOrPreviousWasEmpty(
                    referenceItineraryLeg.agencyName,
                    candidateItineraryLeg.agencyName
                ) ||
                !equalsIgnoreCaseOrPreviousWasEmpty(
                    referenceItineraryLeg.routeLongName,
                    candidateItineraryLeg.routeLongName
                ) ||
                !equalsIgnoreCaseOrPreviousWasEmpty(
                    referenceItineraryLeg.routeShortName,
                    candidateItineraryLeg.routeShortName
                ) ||
                !equalsIgnoreCaseOrPreviousWasEmpty(
                    referenceItineraryLeg.headsign,
                    candidateItineraryLeg.headsign
                ) ||
                (referenceItineraryLeg.interlineWithPreviousLeg != candidateItineraryLeg.interlineWithPreviousLeg)
        ) {
            return false;
        }

        // Make sure the transit trips are scheduled for the same time of the day. A check is being done for the exact
        // scheduled time in order for the trip monitor to attempt to track a specific trip. It is assumed that trip IDs
        // will change over time and as far as an end-user is concerned if, as long as the same route comes at the same
        // time to the same start and end stops, then it can be considered a match.
        if (
            !timeOfDayMatches(
                referenceItineraryLeg.getScheduledStartTime(),
                candidateItineraryLeg.getScheduledStartTime()
            ) || !timeOfDayMatches(
                referenceItineraryLeg.getScheduledEndTime(),
                candidateItineraryLeg.getScheduledEndTime()
            )
        ) {
            return false;
        }

        // if this point is reached, the legs are assumed to match
        return true;
    }

    /**
     * Checks whether two stops (OTP Places) match for the purposes of matching itineraries
     */
    private static boolean stopsMatch(Place stopA, Place stopB) {
        // Stop names must match. It's possible in OTP to have a null place name, although it probably won't occur with
        // transit legs. But just in case this method is expanded in scope to check more stuff about a place, if both
        // are null, then assume a match.
        if (
            (stopA.name != null && !stopA.name.equalsIgnoreCase(stopB.name)) ||
                (stopA.name == null && stopB.name != null)
        ) {
            return false;
        }

        // stop code must match
        if (!equalsIgnoreCaseOrPreviousWasEmpty(stopA.stopCode, stopB.stopCode)) return false;

        // stop positions must be no further than 5 meters apart
        double stopDistanceMeters = DistanceUtils.radians2Dist(
            DistanceUtils.distHaversineRAD(stopA.lat, stopA.lon, stopB.lat, stopB.lon),
            DistanceUtils.EARTH_MEAN_RADIUS_KM
        ) * 1000;
        if (stopDistanceMeters > 5) {
            return false;
        }

        // if this point is reached, the stops are assumed to match
        return true;
    }

    /**
     * Returns true if the reference value is null. Otherwise, returns Objects.equals.
     */
    private static boolean equalsOrPreviousWasNull (Object reference, Object candidate) {
        return reference == null || Objects.equals(reference, candidate);
    }

    /**
     * Returns true if the reference string was not present either by being null or an emptry string. Otherwise, returns
     * if the strings are equal ignoring case.
     */
    private static boolean equalsIgnoreCaseOrPreviousWasEmpty(String reference, String candidate) {
        return StringUtils.isEmpty(reference) || reference.equalsIgnoreCase(candidate);
    }

    /**
     * Returns true if both times have the same hour, minute and second.
     */
    private static boolean timeOfDayMatches(ZonedDateTime zonedDateTimeA, ZonedDateTime zonedDateTimeB) {
        return zonedDateTimeA.getHour() == zonedDateTimeB.getHour() &&
            zonedDateTimeA.getMinute() == zonedDateTimeB.getMinute() &&
            zonedDateTimeA.getSecond() == zonedDateTimeB.getSecond();
    }
}
