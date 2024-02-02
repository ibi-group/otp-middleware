package org.opentripplanner.middleware.controllers.api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.triptracker.ManageLegTraversal;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getTimeInMilliseconds;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.interpolatePoints;

public class ManageLegTraversalTest extends OtpMiddlewareTestEnvironment {

    private static MonitoredTrip monitoredTrip;

    private static TrackedJourney trackedJourney;

    private static final int SUBWAY_LEG_ID = 1;

    private static final int FINAL_LEG_ID = 2;

    @BeforeAll
    public static void setUp() throws IOException {
        assumeTrue(IS_END_TO_END);
        setAuthDisabled(false);
        String payload = CommonTestUtils.getTestResourceAsString("controllers/api/monitored-trip-for-tracking.json");
        monitoredTrip = JsonUtils.getPOJOFromJSON(payload, MonitoredTrip.class);
        Persistence.monitoredTrips.create(monitoredTrip);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        assumeTrue(IS_END_TO_END);
        restoreDefaultAuthDisabled();
        monitoredTrip = Persistence.monitoredTrips.getById(monitoredTrip.id);
        if (monitoredTrip != null) monitoredTrip.delete();
    }

    @AfterEach
    public void tearDownAfterTest() {
        if (trackedJourney != null) {
            trackedJourney.delete();
            trackedJourney = null;
        }
    }

    // This is WIP. These real points and time are nowhere near the expected. This is probably due to the speed of the
    // subway.
    @Test
    void canTrackSubwayTrip() throws IOException {
        String traceTimes = CommonTestUtils.getTestResourceAsString("controllers/api/monitored-trip-trace-times.csv");
        String[] traces = traceTimes.split("\n");
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.tripId = monitoredTrip.id;
        for (String trace : traces) {
            String[] traceValues = trace.split(",");
            TrackingLocation trackingLocation = new TrackingLocation();
            ZonedDateTime zdt = ZonedDateTime.parse(traceValues[0]);
            trackingLocation.timestamp = new Date(zdt.toInstant().toEpochMilli());
            trackingLocation.lat = Double.parseDouble(traceValues[1]);
            trackingLocation.lon = Double.parseDouble(traceValues[2]);
            trackedJourney.locations = List.of(trackingLocation);
            TripStatus tripStatus = ManageTripTracking.getTripStatus(trackedJourney, monitoredTrip);
        }
    }

    @Test
    void canTrackTripWithoutDeviating() {
        for (int legId = 0; legId < monitoredTrip.itinerary.legs.size(); legId++) {
            List<ManageLegTraversal.Segment> segments = createSegmentsForLeg(legId);
            TrackedJourney trackedJourney = new TrackedJourney();
            trackedJourney.tripId = monitoredTrip.id;
            ZonedDateTime startOfTrip = monitoredTrip.itinerary.legs.get(legId).getScheduledStartTime();

            ZonedDateTime currentTime = startOfTrip;
            double cumulativeTravelTime = 0;
            for (ManageLegTraversal.Segment segment : segments) {
                trackedJourney.locations = List.of(
                    new TrackingLocation(
                        segment.coordinates.lat,
                        segment.coordinates.lon,
                        new Date((currentTime.toInstant().toEpochMilli()))
                    )
                );
                assertEquals(
                    ManageTripTracking.getTripStatus(trackedJourney, monitoredTrip).name(),
                    TripStatus.ON_TRACK.name()
                );
                cumulativeTravelTime += segment.timeInSegment;
                currentTime = startOfTrip.plus(getTimeInMilliseconds(cumulativeTravelTime), ChronoUnit.MILLIS);
            }
        }
    }

    @Test
    void cumulativeSegmentTimeMatchesSubwayLegDuration() {
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg(SUBWAY_LEG_ID);
        double cumulative = 0;
        for (ManageLegTraversal.Segment segment : segments) {
            cumulative += segment.timeInSegment;
        }
        assertEquals(monitoredTrip.itinerary.legs.get(SUBWAY_LEG_ID).duration, Math.round(cumulative));
    }

    @Test
    void cumulativeSegmentTimeMatchesWalkLegDuration() {
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg(FINAL_LEG_ID);
        double cumulative = 0;
        for (ManageLegTraversal.Segment segment : segments) {
            cumulative += segment.timeInSegment;
        }
        assertEquals(monitoredTrip.itinerary.legs.get(FINAL_LEG_ID).duration, cumulative);
    }

    @ParameterizedTest
    @MethodSource("createTravelerPositions")
    void canReturnTheCorrectSegmentCoordinates(TravelerPosition segmentPosition) {
        Coordinates actualCoordinates = ManageLegTraversal.getSegmentPosition(
            segmentPosition.start,
            segmentPosition.currentTime,
            segmentPosition.segments
        );
        assertEquals(segmentPosition.coordinates, actualCoordinates);
    }

    private static Stream<TravelerPosition> createTravelerPositions() {
        ZonedDateTime segmentStartTime = ZonedDateTime.now();
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg(FINAL_LEG_ID);
        for (ManageLegTraversal.Segment segment : segments) {
            segment.timeInSegment = 10;
        }

        return Stream.of(
            new TravelerPosition(
                segments.get(0).coordinates,
                segmentStartTime,
                ZonedDateTime.now().plusSeconds(5),
                segments
            ),
            new TravelerPosition(
                segments.get(1).coordinates,
                segmentStartTime,
                ZonedDateTime.now().plusSeconds(15),
                segments
            ),
            new TravelerPosition(
                segments.get(2).coordinates,
                segmentStartTime,
                ZonedDateTime.now().plusSeconds(25),
                segments
            ),
            new TravelerPosition(
                segments.get(3).coordinates,
                segmentStartTime,
                ZonedDateTime.now().plusSeconds(35),
                segments
            )
        );
    }

    private static class TravelerPosition {

        public Coordinates coordinates;

        public ZonedDateTime start;

        public ZonedDateTime currentTime;

        List<ManageLegTraversal.Segment> segments;

        public TravelerPosition(
            Coordinates coordinates,
            ZonedDateTime start,
            ZonedDateTime currentTime,
            List<ManageLegTraversal.Segment> segments
        ) {
            this.coordinates = coordinates;
            this.start = start;
            this.currentTime = currentTime;
            this.segments = segments;
        }
    }

    private static List<ManageLegTraversal.Segment> createSegmentsForLeg(int legId) {
        return interpolatePoints(monitoredTrip.itinerary.legs.get(legId));
    }

}
