package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.I18nUtils;

import java.time.Instant;
import java.util.Locale;

import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getExpectedLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getNextLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromPosition;
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
        expectedLeg = getExpectedLeg(currentPosition, itinerary);
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
     * Whether someone is in 'upcoming' walking range of the origin/departure stop of a transit leg.
     */
    public boolean isNearTransitLegOrigin() {
        double distance1 = distanceToTransitLegOrigin(currentPosition, expectedLeg);
        double distance2 = distanceToTransitLegOrigin(currentPosition, nextLeg);

        return distance1 <= TRIP_INSTRUCTION_UPCOMING_RADIUS || distance2 <= TRIP_INSTRUCTION_UPCOMING_RADIUS;
    }

    private static double distanceToTransitLegOrigin(Coordinates position, Leg leg) {
        return leg != null && leg.transitLeg && leg.from != null
            ? getDistance(position, leg.from.toCoordinates())
            : Double.MAX_VALUE;
    }

    /** Computes the current deviation in meters from the expected itinerary. */
    public double getDeviationMeters() {
        return getDistanceFromLine(legSegmentFromPosition.start, legSegmentFromPosition.end, currentPosition);
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
