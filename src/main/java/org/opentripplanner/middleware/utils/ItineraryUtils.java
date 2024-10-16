package org.opentripplanner.middleware.utils;

import com.spatial4j.core.distance.DistanceUtils;
import org.apache.commons.lang3.StringUtils;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpGraphQLTransportMode;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Agency;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Route;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;

/**
 * A utility class for dealing with OTP queries and itineraries.
 */
public class ItineraryUtils {

    public static final int ITINERARY_CHECK_WINDOW = 7;
    public static final int SERVICE_DAY_START_HOUR = getConfigPropertyAsInt("SERVICE_DAY_START_HOUR", 3);

    /**
     * Generates itinerary request data for the desired dates, based on the provided query parameters.
     * @param params The base OTP GraphQL query parameters.
     * @param dates a list of the desired dates in YYYY-MM-DD format.
     * @return a list of request data for the corresponding request dates.
     */
    public static List<OtpRequest> getOtpRequestsForDates(OtpGraphQLVariables params, List<ZonedDateTime> dates) {
        // Create a copy of the original params in which we change the date.
        List<OtpRequest> requests = new ArrayList<>();
        for (ZonedDateTime date : dates) {
            // Get updated date string and add to params copy.
            OtpGraphQLVariables paramsCopy = params.clone();
            paramsCopy.date = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
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
    public static List<ZonedDateTime> getDatesToCheckItineraryExistence(MonitoredTrip trip) {
        // Start from the query date, if available.
        String startingDateString = trip.otp2QueryParams.date;
        // If there is no query date, start from today.
        LocalDate startingDate = DateTimeUtils.getDateFromQueryDateString(startingDateString);
        ZonedDateTime startingDateTime = trip.tripZonedDateTime(startingDate);

        // Get the dates to check starting from the query date and continuing through the full date range window.
        List<ZonedDateTime> datesToCheck = new ArrayList<>();
        for (int i = 0; i < ITINERARY_CHECK_WINDOW; i++) {
            datesToCheck.add(startingDateTime.plusDays(i));
        }

        return datesToCheck;
    }

    /**
     * Derives the set of modes for the mode query param that is needed to recreate an OTP {@link Itinerary} using the
     * plan trip endpoint.
     */
    public static Set<OtpGraphQLTransportMode> deriveModesFromItinerary(Itinerary itinerary) {
        Set<OtpGraphQLTransportMode> modes = itinerary.legs.stream()
            .map(leg -> {
                OtpGraphQLTransportMode graphQLMode = new OtpGraphQLTransportMode();
                graphQLMode.mode = leg.mode;
                if ("BICYCLE".equals(leg.mode) || "SCOOTER".equals(leg.mode)) {
                    // Field 'rentedbike' includes rented bikes and rented scooters.
                    if (leg.rentedBike) graphQLMode.qualifier = "RENT";
                }
                return graphQLMode;
            })
            .collect(Collectors.toSet());

        // Remove WALK if non-car access modes are present (i.e. {BICYCLE|SCOOTER}[_RENT]).
        // Removing WALK is necessary for OTP to return certain bicycle+transit itineraries.
        // Including WALK is necessary for OTP to return certain car+transit itineraries.
        // In OTP2: WALK is implied if a transit mode is also present and can be removed in those cases.
        boolean hasAccessModes = modes.stream().anyMatch(mode -> List.of("BICYCLE", "SCOOTER").contains(mode.mode));
        if (hasAccessModes || itinerary.hasTransit()) {
            modes.removeIf(m -> "WALK".equals(m.mode));
        }

        // Replace the "CAR" in the set of modes with the correct CAR query mode (CAR_PARK, CAR_RENT, CAR_HAIL)
        // (assuming there is only one car leg in an itinerary).
        Optional<Leg> firstCarLeg = itinerary.legs.stream().filter(leg -> "CAR".equals(leg.mode)).findFirst();
        boolean hasCarAndTransit = firstCarLeg.isPresent() && itinerary.hasTransit();
        if (hasCarAndTransit) {
            Leg carLeg = firstCarLeg.get();
            String carQualifier;

            if (Boolean.TRUE.equals(carLeg.rentedCar)) {
                carQualifier = "RENT";
            } else if (Boolean.TRUE.equals(carLeg.hailedCar)) {
                carQualifier = "HAIL";
            } else {
                carQualifier = "PARK";
            }
            modes.stream().filter(m -> "CAR".equals(m.mode)).forEach(m -> m.qualifier = carQualifier);
        }
        return modes;
    }

    /**
     * Returns true if the itineraries match for the purposes of trip monitoring.
     *
     * @param referenceItinerary The reference itinerary that others are compared against.
     * @param candidateItinerary A new itinerary that might match the previous itinerary.
     */
    public static boolean itinerariesMatch(Itinerary referenceItinerary, Itinerary candidateItinerary) {
        // Make sure both itineraries are monitorable before continuing.
        if (!referenceItinerary.canBeMonitored() || !candidateItinerary.canBeMonitored()) return false;

        // make sure itineraries have same amount of legs
        if (referenceItinerary.legs.size() != candidateItinerary.legs.size()) return false;

        // make sure each leg matches
        for (int i = 0; i < referenceItinerary.legs.size(); i++) {
            Leg referenceItineraryLeg = referenceItinerary.legs.get(i);
            Leg candidateItineraryLeg = candidateItinerary.legs.get(i);

            if (!legsMatch(referenceItineraryLeg, candidateItineraryLeg)) return false;
        }

        // if this point is reached, the itineraries are assumed to match
        return true;
    }

    /**
     * Checks that the specified itinerary is on the same day as the specified date/time.
     * @param itinerary the itinerary to check.
     * @param requestDateTime the request date/time to check, in the OTP's time zone.
     * @param arriveBy true to check the itinerary endtime, false to check the startTime.
     * @return true if the itinerary's startTime or endTime is one the same service day as the day of the specified date and time.
     */
    public static boolean occursOnSameServiceDay(Itinerary itinerary, ZonedDateTime requestDateTime, boolean arriveBy) {
        // Convert dateTimes to dates for date comparison.
        LocalDate date = requestDateTime.toLocalDate();
        ZonedDateTime tripTime = itinerary.getTripTime(arriveBy);
        LocalDate tripDate = tripTime.toLocalDate();
        // If time to check is before service day start,
        // offset the trip date by one day to compensate.
        if(!isAfterServiceStart(requestDateTime)) {
            tripDate = tripDate.plusDays(1);
        }
        // If trip time is after service start (3am or later), the date must match the trip date.
        // Otherwise, the trip is considered to fall on the previous day.
        return isAfterServiceStart(tripTime)
            ? date.equals(tripDate)
            : date.equals(tripDate.minusDays(1));
    }

    /**
     * Check that the input date/time occurs after the start of the service day.
     */
    private static boolean isAfterServiceStart(ZonedDateTime time) {
        return time.getHour() >= SERVICE_DAY_START_HOUR;
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
            !equalsOrReferenceWasNull(referenceItineraryLeg.mode, candidateItineraryLeg.mode) ||
            !agenciesMatch(referenceItineraryLeg.agency, candidateItineraryLeg.agency) ||
            !routesMatch(referenceItineraryLeg.route, candidateItineraryLeg.route) ||
            !equalsIgnoreCaseOrReferenceWasEmpty(referenceItineraryLeg.headsign, candidateItineraryLeg.headsign) ||
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
        if (
            stopA.stop != null &&
            stopB.stop != null &&
            !equalsIgnoreCaseOrReferenceWasEmpty(stopA.stop.code, stopB.stop.code)
        ) {
            return false;
        }

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
     * Checks whether two agencies are deemed the same for itinerary comparison purposes.
     */
    private static boolean agenciesMatch(Agency agencyA, Agency agencyB) {
        return (
            agencyA != null &&
            agencyB != null &&
            equalsIgnoreCaseOrReferenceWasEmpty(agencyA.name, agencyB.name)
        );
    }

    /**
     * Checks whether two transit routes are deemed the same for itinerary comparison purposes.
     */
    private static boolean routesMatch(Route routeA, Route routeB) {
        return (
            routeA != null &&
            routeB != null &&
            equalsIgnoreCaseOrReferenceWasEmpty(routeA.longName, routeB.longName) &&
            equalsIgnoreCaseOrReferenceWasEmpty(routeA.shortName, routeB.shortName)
        );
    }

    /**
     * Returns true if the reference value is null. Otherwise, returns Objects.equals.
     */
    private static boolean equalsOrReferenceWasNull(Object reference, Object candidate) {
        return reference == null || Objects.equals(reference, candidate);
    }

    /**
     * Returns true if the reference string was not present either by being null or an emptry string. Otherwise, returns
     * if the strings are equal ignoring case.
     */
    private static boolean equalsIgnoreCaseOrReferenceWasEmpty(String reference, String candidate) {
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

    /**
     * Make sure the leg in question is a bus transit leg.
     */
    public static boolean isBusLeg(Leg leg) {
        return leg != null && leg.mode.equalsIgnoreCase("BUS") && leg.transitLeg;
    }

    /**
     * Get the second element from the OTP id by removing the OTP agency prefix.
     * E.g. GwinnettCountyTransit:GCT will return just GCT.
     */
    public static String removeAgencyPrefix(String idParts) {
        return (idParts != null) ? idParts.split(":")[1] : null;
    }

    /**
     * Get the route id from leg.
     */
    public static String getRouteIdFromLeg(Leg leg) {
        return (leg != null) ? leg.routeId : null;
    }

    /**
     * Get the agency id from leg.
     */
    public static String getAgencyIdFromLeg(Leg leg) {
        return (leg != null) ? leg.agencyId : null;
    }

    /**
     * Get the trip id from leg.
     */
    public static String getTripIdFromLeg(Leg leg) {
        return (leg != null) ? leg.tripId : null;
    }

    /**
     * Get the stop id from place.
     */
    public static String getStopIdFromPlace(Place place) {
        return (place != null) ? place.stopId : null;
    }

    /**
     * Get the route short name from leg.
     */
    public static String getRouteShortNameFromLeg(Leg leg) {
        return leg.routeShortName;
    }
}
