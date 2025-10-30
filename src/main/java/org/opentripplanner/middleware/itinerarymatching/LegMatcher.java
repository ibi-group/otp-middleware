package org.opentripplanner.middleware.itinerarymatching;

import com.spatial4j.core.distance.DistanceUtils;
import org.apache.commons.lang3.StringUtils;
import org.opentripplanner.middleware.otp.response.Agency;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Route;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Helper class for matching itineraries and legs.
 */
public class LegMatcher {

    private final Leg referenceLeg;
    private final Leg candidateLeg;
    private String failingReason;

    /**
     * Creates a new instance with two itineraries.
     *
     * @param referenceLeg The reference itinerary that others are compared against.
     * @param candidateLeg A new itinerary that might match the previous itinerary.
     */
    public LegMatcher(Leg referenceLeg, Leg candidateLeg) {
        this.referenceLeg = referenceLeg;
        this.candidateLeg = candidateLeg;
    }

    /**
     * Check that two legs match.
     */
    public boolean match() {
        // For now, do not analyze non-transit legs.
        if (!referenceLeg.transitLeg) return true;

        // Make sure the same from/to stop are being used.
        if (!stopsMatch(referenceLeg.from, candidateLeg.from)) {
            failingReason = "Leg origin stops do not match.";
            return false;
        }
        if (!stopsMatch(referenceLeg.to, candidateLeg.to)) {
            failingReason = "Leg destination stops do not match.";
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
            !equalsOrReferenceWasNull(referenceLeg.mode, candidateLeg.mode) ||
            !agenciesMatch(referenceLeg.agency, candidateLeg.agency) ||
            !routesMatch(referenceLeg.route, candidateLeg.route) ||
            !equalsIgnoreCaseOrReferenceWasEmpty(referenceLeg.headsign, candidateLeg.headsign) ||
            (referenceLeg.interlineWithPreviousLeg != candidateLeg.interlineWithPreviousLeg)
        ) {
            return false;
        }

        // Make sure the transit trips are scheduled for the same time of the day. A check is being done for the exact
        // scheduled time in order for the trip monitor to attempt to track a specific trip. It is assumed that trip IDs
        // will change over time and as far as an end-user is concerned if, as long as the same route comes at the same
        // time to the same start and end stops, then it can be considered a match.
        if (
            !timeOfDayMatches(
                referenceLeg.getScheduledStartTime(),
                candidateLeg.getScheduledStartTime()
            ) || !timeOfDayMatches(
                referenceLeg.getScheduledEndTime(),
                candidateLeg.getScheduledEndTime()
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

    public String getFailingReason() {
        return failingReason;
    }
}
