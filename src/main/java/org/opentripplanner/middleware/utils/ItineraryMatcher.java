package org.opentripplanner.middleware.utils;

import com.spatial4j.core.distance.DistanceUtils;
import org.apache.commons.lang3.StringUtils;
import org.opentripplanner.middleware.otp.response.Agency;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Route;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Helper class for matching itineraries and legs.
 */
public class ItineraryMatcher {

    private final Itinerary referenceItinerary;
    private final Itinerary candidateItinerary;
    private String failingReason;

    /**
     * Creates a new instance with two itineraries.
     *
     * @param referenceItinerary The reference itinerary that others are compared against.
     * @param candidateItinerary A new itinerary that might match the previous itinerary.
     */
    public ItineraryMatcher(Itinerary referenceItinerary, Itinerary candidateItinerary) {
        this.referenceItinerary = referenceItinerary;
        this.candidateItinerary = candidateItinerary;
    }

    /**
     * Returns true if the itineraries match for the purposes of trip monitoring.
     */
    public boolean match() {
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
     * Check whether a new leg of an itinerary matches the previous itinerary leg for the purposes of trip monitoring.
     */
    public static boolean legsMatch(Leg referenceItineraryLeg, Leg candidateItineraryLeg) {
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
        // service is the same experience to a customer if the following conditions are met:
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
}
