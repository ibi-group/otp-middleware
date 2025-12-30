package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.OtpGraphQLTransportMode;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.OtpRequest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.itinerarymatching.ItineraryFromLegMatcher.getTransitLegs;
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

            if (Boolean.TRUE.equals(carLeg.rentedBike)) {
                carQualifier = "RENT";
            } else if (carLeg.rideHailingEstimate != null) {
                carQualifier = "HAIL";
            } else {
                carQualifier = "PARK";
            }
            modes.stream().filter(m -> "CAR".equals(m.mode)).forEach(m -> m.qualifier = carQualifier);
        }
        return modes;
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
     * Get the route GTFS id from leg.
     */
    public static String getRouteGtfsIdFromLeg(Leg leg) {
        return (leg != null && leg.route != null) ? leg.route.gtfsId : null;
    }

    /**
     * Get the agency GTFS id from leg.
     */
    public static String getAgencyGtfsIdFromLeg(Leg leg) {
        return (leg != null && leg.agency != null) ? leg.agency.gtfsId : null;
    }

    /**
     * Get the trip GTFS id from leg.
     */
    public static String getTripGtfsIdFromLeg(Leg leg) {
        return (leg != null && leg.trip != null) ? leg.trip.gtfsId : null;
    }

    /**
     * Get the stop GTFS id from place.
     */
    public static String getStopGtfsIdFromPlace(Place place) {
        return (place != null && place.stop != null) ? place.stop.gtfsId : null;
    }

    /**
     * Get the route short name from leg.
     */
    public static String getRouteShortNameFromLeg(Leg leg) {
        return (leg != null && leg.route != null) ? leg.route.shortName : null;
    }

    /**
     * Get the first leg in an itinerary.
     */
    public static Leg getFirstLeg(Itinerary itinerary) {
        return Optional
            .ofNullable(itinerary)
            .map(itin -> itin.legs)
            .map(legs -> legs.get(0))
            .orElse(null);
    }

    /**
     * Whether there are transit legs that remain to be completed at the current clock time.
     */
    public static boolean remainingTransitLegs(Itinerary itinerary) {
        Date now = DateTimeUtils.nowAsDate();
        return getTransitLegs(itinerary.legs).stream().anyMatch(leg -> now.before(leg.endTime));
    }
}
