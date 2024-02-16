package org.opentripplanner.middleware.controllers.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.triptracker.ManageLegTraversal;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSecondsToMilliseconds;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.interpolatePoints;

public class ManageLegTraversalTest {

    private static MonitoredTrip monitoredTrip;
    private static MonitoredTrip busStopJusticeCenterTrip;

    private static final int SUBWAY_LEG_ID = 1;

    private static final int FINAL_LEG_ID = 2;

    @BeforeAll
    public static void setUp() throws IOException {
        monitoredTrip = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/monitored-trip-for-tracking.json"),
            MonitoredTrip.class
        );
        busStopJusticeCenterTrip = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/bus-stop-justice-center-trip.json"),
            MonitoredTrip.class
        );
    }

    @ParameterizedTest
    @MethodSource("createTrace")
    void canTrackTrip(String time, double lat, double lon, TripStatus expected) {
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.tripId = busStopJusticeCenterTrip.id;
        TrackingLocation trackingLocation = new TrackingLocation(time, lat, lon);
        trackedJourney.locations = List.of(trackingLocation);
        TripStatus tripStatus = ManageTripTracking.getTripStatus(trackedJourney, busStopJusticeCenterTrip);
        assertEquals(expected, tripStatus);
    }

    private static Stream<Arguments> createTrace() {
        return Stream.of(
            Arguments.of("2024-01-26T19:07:05Z", 33.95022, -83.9906, TripStatus.BEHIND_SCHEDULE),
            Arguments.of("2024-01-26T19:07:08Z", 33.95022, -83.9906, TripStatus.ON_SCHEDULE),
            Arguments.of("2024-01-26T19:07:50Z", 33.95022, -83.9906, TripStatus.AHEAD_OF_SCHEDULE),
            Arguments.of("2024-01-26T19:07:43Z", 33.6408211227185, -84.4465224660685, TripStatus.DEVIATED),
            Arguments.of("2024-01-26T19:12:25Z", 33.9517224, -83.9929082, TripStatus.NO_STATUS)
        );
    }

    @Test
    void canTrackLegWithoutDeviating() {
        for (int legId = 0; legId < monitoredTrip.itinerary.legs.size(); legId++) {
            List<ManageLegTraversal.Segment> segments = createSegmentsForLeg(legId);
            TrackedJourney trackedJourney = new TrackedJourney();
            trackedJourney.tripId = monitoredTrip.id;
            ZonedDateTime startOfTrip = ZonedDateTime.ofInstant(
                monitoredTrip.itinerary.legs.get(legId).startTime.toInstant(),
                DateTimeUtils.getOtpZoneId()
            );

            ZonedDateTime currentTime = startOfTrip;
            double cumulativeTravelTime = 0;
            for (ManageLegTraversal.Segment segment : segments) {
                trackedJourney.locations = List.of(
                    new TrackingLocation(
                        segment.coordinates.lat,
                        segment.coordinates.lon,
                        new Date(currentTime.toInstant().toEpochMilli())
                    )
                );
                assertEquals(
                    ManageTripTracking.getTripStatus(trackedJourney, monitoredTrip).name(),
                    TripStatus.ON_SCHEDULE.name()
                );
                cumulativeTravelTime += segment.timeInSegment;
                currentTime = startOfTrip.plus(getSecondsToMilliseconds(cumulativeTravelTime), ChronoUnit.MILLIS);
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
    void canReturnTheCorrectSegmentCoordinates(TravellerPosition segmentPosition) {
        ManageLegTraversal.Segment segment = ManageLegTraversal.getSegmentPosition(
            segmentPosition.start,
            segmentPosition.currentTime,
            segmentPosition.segments
        );
        assert segment != null;
        assertEquals(segmentPosition.coordinates, segment.coordinates);
    }

    private static Stream<TravellerPosition> createTravelerPositions() {
        Instant segmentStartTime = ZonedDateTime.now().toInstant();
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg(FINAL_LEG_ID);
        for (ManageLegTraversal.Segment segment : segments) {
            segment.timeInSegment = 10;
        }

        return Stream.of(
            new TravellerPosition(
                segments.get(0).coordinates,
                segmentStartTime,
                segmentStartTime.plusSeconds(5),
                segments
            ),
            new TravellerPosition(
                segments.get(1).coordinates,
                segmentStartTime,
                segmentStartTime.plusSeconds(15),
                segments
            ),
            new TravellerPosition(
                segments.get(2).coordinates,
                segmentStartTime,
                segmentStartTime.plusSeconds(25),
                segments
            ),
            new TravellerPosition(
                segments.get(3).coordinates,
                segmentStartTime,
                segmentStartTime.plusSeconds(35),
                segments
            )
        );
    }

    private static class TravellerPosition {

        public Coordinates coordinates;

        public Instant start;

        public Instant currentTime;

        List<ManageLegTraversal.Segment> segments;

        public TravellerPosition(
            Coordinates coordinates,
            Instant start,
            Instant currentTime,
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
