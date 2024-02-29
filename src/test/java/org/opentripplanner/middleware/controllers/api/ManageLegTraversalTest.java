package org.opentripplanner.middleware.controllers.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSecondsToMilliseconds;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.interpolatePoints;

public class ManageLegTraversalTest {

    private static Itinerary busStopToJusticeCenterItinerary;

    @BeforeAll
    public static void setUp() throws IOException {
        busStopToJusticeCenterItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/bus-stop-justice-center-trip.json"),
            Itinerary.class
        );
    }

    @ParameterizedTest
    @MethodSource("createTrace")
    void canTrackTrip(String time, double lat, double lon, TripStatus expected) {
        TrackedJourney trackedJourney = new TrackedJourney();
        TrackingLocation trackingLocation = new TrackingLocation(time, lat, lon);
        trackedJourney.locations = List.of(trackingLocation);
        TripStatus tripStatus = ManageTripTracking.getTripStatus(trackedJourney, busStopToJusticeCenterItinerary);
        assertEquals(expected, tripStatus);
    }

    private static Stream<Arguments> createTrace() {
        Date startTime = busStopToJusticeCenterItinerary.startTime;
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg();
        ManageLegTraversal.Segment before = segments.get(8);
        ManageLegTraversal.Segment current = segments.get(10);
        ManageLegTraversal.Segment after = segments.get(12);
        return Stream.of(
            Arguments.of(
                getDateTimeAsString(startTime, before.cumulativeTime),
                current.start.lat,
                current.start.lon,
                TripStatus.BEHIND_SCHEDULE
            ),
            Arguments.of(
                getDateTimeAsString(startTime, current.cumulativeTime),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE
            ),
            Arguments.of(
                getDateTimeAsString(startTime, after.cumulativeTime),
                current.start.lat,
                current.start.lon,
                TripStatus.AHEAD_OF_SCHEDULE
            ),
            // Time which can not be attributed to a trip leg.
            Arguments.of(
                getDateTimeAsString(busStopToJusticeCenterItinerary.endTime, 1),
                current.start.lat,
                current.start.lon,
                TripStatus.NO_STATUS
            ),
            // Arbitrary lat/lon values which aren't on the trip.
            Arguments.of(
                getDateTimeAsString(startTime, 0),
                33.95029,
                -83.99,
                TripStatus.DEVIATED
            )
        );
    }

    @Test
    void canAccumulateCorrectStartAndEndCoordinates() {
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg();
        for (int i=0; i < segments.size()-1; i++) {
            ManageLegTraversal.Segment segmentOne = segments.get(i);
            ManageLegTraversal.Segment segmentTwo = segments.get(i+1);
            assertEquals(segmentOne.end.lat, segmentTwo.start.lat);
        }
    }

    @Test
    void canTrackLegWithoutDeviating() {
        for (int legIndex = 0; legIndex < busStopToJusticeCenterItinerary.legs.size(); legIndex++) {
            List<ManageLegTraversal.Segment> segments = createSegmentsForLeg();
            TrackedJourney trackedJourney = new TrackedJourney();
            ZonedDateTime startOfTrip = ZonedDateTime.ofInstant(
                busStopToJusticeCenterItinerary.legs.get(legIndex).startTime.toInstant(),
                DateTimeUtils.getOtpZoneId()
            );

            ZonedDateTime currentTime = startOfTrip;
            double cumulativeTravelTime = 0;
            for (ManageLegTraversal.Segment segment : segments) {
                trackedJourney.locations = List.of(
                    new TrackingLocation(
                        segment.start.lat,
                        segment.start.lon,
                        new Date(currentTime.toInstant().toEpochMilli())
                    )
                );
                assertEquals(
                    ManageTripTracking.getTripStatus(trackedJourney, busStopToJusticeCenterItinerary).name(),
                    TripStatus.ON_SCHEDULE.name()
                );
                cumulativeTravelTime += segment.timeInSegment;
                currentTime = startOfTrip.plus(getSecondsToMilliseconds(cumulativeTravelTime), ChronoUnit.MILLIS);
            }
        }
    }

    @Test
    void cumulativeSegmentTimeMatchesWalkLegDuration() {
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg();
        double cumulative = 0;
        for (ManageLegTraversal.Segment segment : segments) {
            cumulative += segment.timeInSegment;
        }
        assertEquals(busStopToJusticeCenterItinerary.legs.get(0).duration, cumulative, 0.01f);
    }

    @ParameterizedTest
    @MethodSource("createTravelerPositions")
    void canReturnTheCorrectSegmentCoordinates(TravellerPosition segmentPosition) {
        ManageLegTraversal.Segment segment = ManageLegTraversal.getSegmentPosition(
            segmentPosition.start,
            segmentPosition.currentTime,
            segmentPosition.segments
        );
        assertNotNull(segment);
        assertEquals(segmentPosition.coordinates, segment.start);
    }

    private static Stream<TravellerPosition> createTravelerPositions() {
        Instant segmentStartTime = ZonedDateTime.now().toInstant();
        List<ManageLegTraversal.Segment> segments = createSegmentsForLeg();
        for (ManageLegTraversal.Segment segment : segments) {
            segment.timeInSegment = 10;
        }

        return Stream.of(
            new TravellerPosition(
                segments.get(0).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(5),
                segments
            ),
            new TravellerPosition(
                segments.get(1).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(15),
                segments
            ),
            new TravellerPosition(
                segments.get(2).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(25),
                segments
            ),
            new TravellerPosition(
                segments.get(3).start,
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

    private static List<ManageLegTraversal.Segment> createSegmentsForLeg() {
        return interpolatePoints(busStopToJusticeCenterItinerary.legs.get(0));
    }

    private static String getDateTimeAsString(Date date, double offset) {
        Instant dateTime = date.toInstant().plusSeconds((long) offset);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault());;
        return formatter.format(dateTime);
    }
}
