package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.I18nUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getExpectedLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getNextLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromPosition;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.injectWaypointsIntoLegPositions;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistanceFromLine;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getFirstLeg;

public class TravelerPosition {

    /** The leg the traveler is expected to be on. */
    public Leg expectedLeg;

    /** The expected traveler position based on position. */
    public LegSegment legSegmentFromPosition;

    /** Traveler current coordinates. */
    public Coordinates currentPosition;

    /** Speed reported at the position, in meters per second. */
    public int speed;

    /** Traveler current time. */
    public Instant currentTime;

    /** Information held about the traveler's journey. */
    public TrackedJourney trackedJourney;

    /** The leg, if available, after the expected leg. */
    public Leg nextLeg;

    /** Traveler mobility information which is passed on to bus operators. */
    public String mobilityMode;

    /** The traveler's locale. */
    public Locale locale;

    /** The first leg of the trip. **/
    public Leg firstLegOfTrip;

    /** Holds waypoints to determine turn-by-turn instructions */
    private List<Coordinates> legPositions;

    public TravelerPosition(Builder builder) {
        this.expectedLeg = builder.expectedLeg;
        this.currentPosition = builder.currentPosition;
        this.speed = builder.speed;
        this.firstLegOfTrip = builder.firstLegOfTrip;
        if (expectedLeg != null && currentPosition != null) {
            this.legSegmentFromPosition = getSegmentFromPosition(expectedLeg, currentPosition);
        }
        this.nextLeg = builder.nextLeg;
        this.currentTime = builder.currentTime;
        this.trackedJourney = builder.trackedJourney;

    }

    public TravelerPosition(TrackedJourney trackedJourney, Itinerary itinerary, OtpUser otpUser) {
        TrackingLocation lastLocation = trackedJourney.locations.get(trackedJourney.locations.size() - 1);
        currentTime = lastLocation.timestamp.toInstant();
        currentPosition = new Coordinates(lastLocation);
        speed = lastLocation.speed;
        expectedLeg = getExpectedLeg(currentPosition, speed, itinerary);
        if (expectedLeg != null) {
            nextLeg = getNextLeg(expectedLeg, itinerary);
        }
        legSegmentFromPosition = getSegmentFromPosition(expectedLeg, currentPosition);
        this.trackedJourney = trackedJourney;
        if (otpUser != null) {
            if (otpUser.mobilityProfile != null) {
                mobilityMode = otpUser.mobilityProfile.mobilityMode;
            }
            this.locale = I18nUtils.getOtpUserLocale(otpUser);
        }
        firstLegOfTrip = getFirstLeg(itinerary);
    }

    /**
     * Returns the closest transit leg in 'upcoming' radius eligible for a "Wait for transit" instruction.
     */
    public Leg getTransitLegWithClosestUpcomingOrigin() {
        return getLegWithClosestUpcomingOrigin(true);
    }

    /**
     * Returns the closest leg in 'upcoming' radius.
     */
    public Leg getLegWithClosestUpcomingOrigin(boolean isTransit) {
        double distanceToExpectedLeg = distanceToLegOrigin(currentPosition, expectedLeg, isTransit);
        double distanceToNextLeg = distanceToLegOrigin(currentPosition, nextLeg, isTransit);

        if (distanceToExpectedLeg <= TRIP_INSTRUCTION_UPCOMING_RADIUS && distanceToExpectedLeg < distanceToNextLeg) {
            return expectedLeg;
        } else if (distanceToNextLeg <= TRIP_INSTRUCTION_UPCOMING_RADIUS && distanceToNextLeg < distanceToExpectedLeg) {
            return nextLeg;
        } else {
            return null;
        }
    }

    private static double distanceToLegOrigin(Coordinates position, Leg leg, boolean isTransit) {
        return leg != null && (!isTransit || leg.transitLeg) && leg.from != null
            ? getDistance(position, leg.from.toCoordinates())
            : Double.MAX_VALUE;
    }

    /** Computes the current deviation in meters from the expected itinerary. */
    public double getDeviationMeters() {
        return getDistanceFromLine(legSegmentFromPosition.start, legSegmentFromPosition.end, currentPosition);
    }

    /**
     * Gets a cached list of leg positions for the given leg.
     * Not null because {@link TravelerLocator#createExclusionZone} returns at least an empty array list.
     */
    public List<Coordinates> getLegPositions() {
        if (legPositions == null) {
            legPositions = injectWaypointsIntoLegPositions(
                expectedLeg,
                expectedLeg.steps,
                TRIP_INSTRUCTION_UPCOMING_RADIUS
            );
        }
        return legPositions;
    }

    /**
     * Builder to handle basic unit test requirements.
     */
    public static final class Builder {

        private Leg expectedLeg;
        private Coordinates currentPosition;
        private int speed;
        private Leg firstLegOfTrip;
        private Leg nextLeg;
        private Instant currentTime;
        private TrackedJourney trackedJourney;

        public Builder setExpectedLeg(Leg expectedLeg) {
            this.expectedLeg = expectedLeg;
            return this;
        }

        public Builder setCurrentPosition(Coordinates currentPosition) {
            this.currentPosition = currentPosition;
            return this;
        }

        public Builder setSpeed(int speed) {
            this.speed = speed;
            return this;
        }

        public Builder setFirstLegOfTrip(Leg firstLegOfTrip) {
            this.firstLegOfTrip = firstLegOfTrip;
            return this;
        }

        public Builder setNextLeg(Leg nextLeg) {
            this.nextLeg = nextLeg;
            return this;
        }

        public Builder setCurrentTime(Instant currentTime) {
            this.currentTime = currentTime;
            return this;
        }

        public Builder setTrackedJourney(TrackedJourney trackedJourney) {
            this.trackedJourney = trackedJourney;
            return this;
        }

        public TravelerPosition build() {
            return new TravelerPosition(this);
        }
    }
}
