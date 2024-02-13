package org.opentripplanner.middleware.controllers.api;

import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.triptracker.ManageLegTraversal;
import org.opentripplanner.middleware.triptracker.ManageTripTracking;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getDistance;
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

    // WIP. Time adjusted so that the provided traveler times start around the same time at the trip.
    @Test
    void canTrackSubwayTrip() throws IOException {
        String traceTimes = CommonTestUtils.getTestResourceAsString("controllers/api/monitored-trip-trace-times.csv");
        String[] traces = traceTimes.split("\n");
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.tripId = monitoredTrip.id;
        HashMap<String, Integer> outcome = new HashMap<>();
        for (String trace : traces) {
            String[] traceValues = trace.split(",");
            trackedJourney.locations = List.of(new TrackingLocation(traceValues[0], 3, 8, traceValues[1], traceValues[2]));
            TripStatus tripStatus = ManageTripTracking.getTripStatus(trackedJourney, monitoredTrip);
            if (outcome.containsKey(tripStatus.name())) {
                Integer hits = outcome.get(tripStatus.name());
                hits++;
                outcome.put(tripStatus.name(), hits);
            } else {
                outcome.put(tripStatus.name(), 1);
            }
        }
        for (TripStatus tripStatus : TripStatus.values()) {
            System.out.println(tripStatus.name() + ": " + outcome.get(tripStatus.name()));
        }
        System.out.println("Total: " + traces.length);
    }


    // WIP.
    @Test
    void canTrackBusStopToJusticeCenterTrip() throws IOException {
        String traceTimes = CommonTestUtils.getTestResourceAsString("controllers/api/bus-stop-justice-center-trace-times.csv");
        String[] traces = traceTimes.split("\n");
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.tripId = busStopJusticeCenterTrip.id;
        HashMap<String, Integer> outcome = new HashMap<>();
        for (String trace : traces) {
            String[] traceValues = trace.split(",");
            TrackingLocation trackingLocation = new TrackingLocation(traceValues[0], traceValues[1], traceValues[2]);
            trackedJourney.locations = List.of(trackingLocation);
            TripStatus tripStatus = ManageTripTracking.getTripStatus(trackedJourney, busStopJusticeCenterTrip);
            if (outcome.containsKey(tripStatus.name())) {
                Integer hits = outcome.get(tripStatus.name());
                hits++;
                outcome.put(tripStatus.name(), hits);
            } else {
                outcome.put(tripStatus.name(), 1);
            }
        }
        for (TripStatus tripStatus : TripStatus.values()) {
            System.out.println(tripStatus.name() + ": " + outcome.get(tripStatus.name()));
        }
        System.out.println("Total: " + traces.length);
    }

    // WIP.
    @Test
    void speedCheck() throws IOException {
        String traceTimes = CommonTestUtils.getTestResourceAsString("controllers/api/bus-stop-justice-center-trace-times.csv");
        String[] traces = traceTimes.split("\n");
        for (int i=0; i<traces.length-1; i++) {
            String[] traceValuesPoint1 = traces[i].split(",");
            String[] traceValuesPoint2 = traces[i+1].split(",");
            ZonedDateTime timePoint1 = ZonedDateTime.parse(traceValuesPoint1[0]);
            Coordinates coordinates1 = new Coordinates(Double.parseDouble(traceValuesPoint1[1]),Double.parseDouble(traceValuesPoint1[2]));
            ZonedDateTime timePoint2 = ZonedDateTime.parse(traceValuesPoint2[0]);
            Coordinates coordinates2 = new Coordinates(Double.parseDouble(traceValuesPoint2[1]),Double.parseDouble(traceValuesPoint2[2]));
            double dist = getDistance(coordinates1, coordinates2);
            long seconds = Duration.between(timePoint1,timePoint2).getSeconds();
            if (seconds == 0) {
                continue;
            }
            double speed = dist / seconds;
            System.out.println(speed + " : " + dist + " : " + seconds);
        }
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
                    TripStatus.ON_TRACK.name()
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
        Coordinates actualCoordinates = ManageLegTraversal.getSegmentPosition(
            segmentPosition.start,
            segmentPosition.currentTime,
            segmentPosition.segments
        );
        assertEquals(segmentPosition.coordinates, actualCoordinates);
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

    @Test
    void generatePointsForBusStopToJusticeCenterTrip() {
        List<Position> positions = PolylineUtils.decode(busStopJusticeCenterTrip.itinerary.legs.get(0).legGeometry.points, 5);
        for (Position position : positions) {
            System.out.println(position.getLatitude() + "," + position.getLongitude() + ",");
        }
    }



}
