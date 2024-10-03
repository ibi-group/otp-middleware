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
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistanceFromLine;

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
    }

    /** Used for unit testing. */
    public TravelerPosition(Leg expectedLeg, Coordinates currentPosition, int speed) {
        this.expectedLeg = expectedLeg;
        this.currentPosition = currentPosition;
        this.speed = speed;
        legSegmentFromPosition = getSegmentFromPosition(expectedLeg, currentPosition);
    }

    /** Used for unit testing. */
    public TravelerPosition(Leg expectedLeg, Coordinates currentPosition) {
        // Anywhere the speed is zero means that speed is not considered for a specific logic.
        this(expectedLeg, currentPosition, 0);
    }

    /** Used for unit testing. */
    public TravelerPosition(Leg nextLeg, Instant currentTime) {
        this.nextLeg = nextLeg;
        this.currentTime = currentTime;
    }

    /** Computes the current deviation in meters from the expected itinerary. */
    public double getDeviationMeters() {
        return getDistanceFromLine(legSegmentFromPosition.start, legSegmentFromPosition.end, currentPosition);
    }
}
