package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;

import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getNearestLegSegment;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getExpectedLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromPosition;

public class TravelerPosition {

    /** The leg the traveler is expected to be on. */
    public Leg expectedLeg;

    /** The expected traveler position based on position. */
    public LegSegment legSegmentFromPosition;

    /** Traveler current coordinates. */
    public Coordinates currentPosition;

    /** Traveler current time. */
    public Instant currentTime;

    /** The leg segment that is nearest to the traveler. */
    public LegSegment nearestLegSegment;

    public TravelerPosition(TrackedJourney trackedJourney, Itinerary itinerary) {
        TrackingLocation lastLocation = trackedJourney.locations.get(trackedJourney.locations.size() - 1);
        currentTime = lastLocation.timestamp.toInstant();
        currentPosition = new Coordinates(lastLocation);
        expectedLeg = getExpectedLeg(currentPosition, itinerary);
        legSegmentFromPosition = getSegmentFromPosition(expectedLeg, currentPosition);
        nearestLegSegment = getNearestLegSegment(currentPosition, itinerary);
    }

    /** Used for unit testing. */
    public TravelerPosition(Leg expectedLeg, Coordinates currentPosition) {
        this.expectedLeg = expectedLeg;
        this.currentPosition = currentPosition;
    }
}
