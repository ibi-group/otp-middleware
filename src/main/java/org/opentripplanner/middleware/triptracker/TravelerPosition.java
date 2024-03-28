package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;

import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getExpectedLeg;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromPosition;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSegmentFromTime;

public class TravelerPosition {

    /** The leg the traveler is expected to be on. */
    public Leg expectedLeg;

    /** The expected traveler position, based on time. */
    public LegSegment legSegmentFromTime;

    /** The expected traveler position, based on coordinates. */
    public LegSegment legSegmentFromPosition;

    /** Traveler current coordinates. */
    public Coordinates currentPosition;

    /** Traveler current time. */
    public Instant currentTime;

    private TravelerPosition() {
        // Disable the default no-arg constructor.
    }

    public TravelerPosition(TrackedJourney trackedJourney, Itinerary itinerary) {
        TrackingLocation lastLocation = trackedJourney.locations.get(trackedJourney.locations.size() - 1);
        currentTime = lastLocation.timestamp.toInstant();
        currentPosition = new Coordinates(lastLocation);
        expectedLeg = getExpectedLeg(lastLocation.timestamp.toInstant(), itinerary);
        legSegmentFromTime = getSegmentFromTime(lastLocation.timestamp.toInstant(), itinerary);
        legSegmentFromPosition = getSegmentFromPosition(expectedLeg, currentPosition);
    }
}
