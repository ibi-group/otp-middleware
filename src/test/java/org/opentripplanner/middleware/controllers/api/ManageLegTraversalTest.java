package org.opentripplanner.middleware.controllers.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.triptracker.ManageLegTraversal;
import org.opentripplanner.middleware.triptracker.StepSegment;
import org.opentripplanner.middleware.triptracker.TripSegment;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripInstruction;
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
import static org.opentripplanner.middleware.triptracker.TripInstruction.IMMEDIATE_PREFIX;
import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.TripInstruction.UPCOMING_PREFIX;
import static org.opentripplanner.middleware.triptracker.TripInstruction.getStepSegments;
import static org.opentripplanner.middleware.triptracker.TripStatus.getSegmentTimeInterval;
import static org.opentripplanner.middleware.utils.GeometryUtils.calculateBearing;
import static org.opentripplanner.middleware.utils.GeometryUtils.createDestinationPoint;

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
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, busStopToJusticeCenterItinerary);
        TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
        String instruction = TripInstruction.getInstruction(tripStatus, travelerPosition);
        assertEquals(expected, tripStatus);
    }

    private static Stream<Arguments> createTrace() {
        Date startTime = busStopToJusticeCenterItinerary.startTime;
        List<TripSegment> tripSegments = createSegmentsForLeg();
        TripSegment before = tripSegments.get(8);
        TripSegment current = tripSegments.get(10);
        TripSegment after = tripSegments.get(12);
        return Stream.of(
            Arguments.of(
                getDateTimeAsString(startTime, getSegmentTimeInterval(before)),
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
            // Slight deviation on time.
            Arguments.of(
                getDateTimeAsString(startTime, current.cumulativeTime - 4),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE
            ),
            // Slight deviation on time.
            Arguments.of(
                getDateTimeAsString(startTime, (current.cumulativeTime - current.timeInSegment) + 4),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE
            ),
            // Slight deviation on lat/lon.
            Arguments.of(
                getDateTimeAsString(startTime, current.cumulativeTime),
                current.start.lat + 0.00001,
                current.start.lon + 0.00001,
                TripStatus.ON_SCHEDULE
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

    @ParameterizedTest
    @MethodSource("createTurnByTurnTrace")
    void canTrackTurnByTurn(TripSegment activeTripSegment, String expectedInstruction) {
        assertEquals(expectedInstruction, TripInstruction.createInstruction(
            busStopToJusticeCenterItinerary.legs.get(0),
            activeTripSegment
        ));
    }

    private static Stream<Arguments> createTurnByTurnTrace() {
        Leg walkLeg = busStopToJusticeCenterItinerary.legs.get(0);
        List<Step> walkSteps = walkLeg.steps;
        Step lastStep = walkSteps.get(walkSteps.size()-1);
        List<StepSegment> stepSegments = getStepSegments(walkLeg);
        StepSegment firstStepSegment = stepSegments.get(0);
        StepSegment secondStep = stepSegments.get(1);
        StepSegment thirdStep = stepSegments.get(2);
        StepSegment fourthStep = stepSegments.get(3);
        StepSegment lastStepSegment = new StepSegment(
            new Coordinates(lastStep),
            new Coordinates(lastStep),
            -1
        );
        double firstStepBearing = calculateBearing(firstStepSegment.start, firstStepSegment.end);
        double fourthStepBearing = calculateBearing(fourthStep.start, fourthStep.end);
        return Stream.of(
            // On step change.
            Arguments.of(new TripSegment(
                firstStepSegment.start, firstStepSegment.end),
                getInstruction(IMMEDIATE_PREFIX, walkSteps.get(1))
            ),
            Arguments.of(new TripSegment(
                secondStep.start, secondStep.end),
                getInstruction(IMMEDIATE_PREFIX, walkSteps.get(2))
            ),
            Arguments.of(new TripSegment(
                thirdStep.start, thirdStep.end),
                getInstruction(IMMEDIATE_PREFIX, walkSteps.get(3))
            ),
            Arguments.of(new TripSegment(
                fourthStep.start, fourthStep.end),
                getInstruction(IMMEDIATE_PREFIX, walkSteps.get(4))
            ),
            // Before first step.
            Arguments.of(createTripSegment(
                firstStepSegment.start, 0.005, firstStepBearing, true),
                getInstruction(UPCOMING_PREFIX, walkSteps.get(0))
            ),
            // At first step.
            Arguments.of(createTripSegment(
                firstStepSegment.start, 0.001, firstStepBearing, true),
                getInstruction(IMMEDIATE_PREFIX, walkSteps.get(0))
            ),
            // Pass first step.
            Arguments.of(createTripSegment(
                firstStepSegment.end, 0.001, firstStepBearing, false),
                getInstruction(UPCOMING_PREFIX, walkSteps.get(2))
            ),
            // Pass last step.
            Arguments.of(createTripSegment(
                lastStepSegment.start, 0.010, 315, false),
                NO_INSTRUCTION
            ),
            // Approaching last step (at 90 degrees to reduce confidence).
            Arguments.of(createTripSegment(
                fourthStep.end, 0.002, fourthStepBearing + 90, true),
                getInstruction(UPCOMING_PREFIX, walkSteps.get(4)
            ))
        );
    }

    private static String getInstruction(String prefix, Step step) {
        return String.format("%s%s", prefix, step.relativeDirection);
    }

    private static TripSegment createTripSegment(Coordinates point, double distance, double bearing, boolean oppositeDirection) {
        if (oppositeDirection) {
            bearing = bearing - 180;
        }
        Coordinates end = createDestinationPoint(point,distance, bearing);
        Coordinates start = createDestinationPoint(point,distance + 0.005, bearing);
        return new TripSegment(start, end);
    }

    @Test
    void canAccumulateCorrectStartAndEndCoordinates() {
        List<TripSegment> tripSegments = createSegmentsForLeg();
        for (int i = 0; i < tripSegments.size()-1; i++) {
            TripSegment tripSegmentOne = tripSegments.get(i);
            TripSegment tripSegmentTwo = tripSegments.get(i+1);
            assertEquals(tripSegmentOne.end.lat, tripSegmentTwo.start.lat);
        }
    }

    @Test
    void canTrackLegWithoutDeviating() {
        for (int legIndex = 0; legIndex < busStopToJusticeCenterItinerary.legs.size(); legIndex++) {
            List<TripSegment> tripSegments = createSegmentsForLeg();
            TrackedJourney trackedJourney = new TrackedJourney();
            ZonedDateTime startOfTrip = ZonedDateTime.ofInstant(
                busStopToJusticeCenterItinerary.legs.get(legIndex).startTime.toInstant(),
                DateTimeUtils.getOtpZoneId()
            );

            ZonedDateTime currentTime = startOfTrip;
            double cumulativeTravelTime = 0;
            for (TripSegment tripSegment : tripSegments) {
                trackedJourney.locations = List.of(
                    new TrackingLocation(
                        tripSegment.start.lat,
                        tripSegment.start.lon,
                        new Date(currentTime.toInstant().toEpochMilli())
                    )
                );
                TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, busStopToJusticeCenterItinerary);
                assertEquals(
                    TripStatus.getTripStatus(travelerPosition).name(),
                    TripStatus.ON_SCHEDULE.name()
                );
                cumulativeTravelTime += tripSegment.timeInSegment;
                currentTime = startOfTrip.plus(getSecondsToMilliseconds(cumulativeTravelTime), ChronoUnit.MILLIS);
            }
        }
    }

    @Test
    void cumulativeSegmentTimeMatchesWalkLegDuration() {
        List<TripSegment> tripSegments = createSegmentsForLeg();
        double cumulative = 0;
        for (TripSegment tripSegment : tripSegments) {
            cumulative += tripSegment.timeInSegment;
        }
        assertEquals(busStopToJusticeCenterItinerary.legs.get(0).duration, cumulative, 0.01f);
    }

    @ParameterizedTest
    @MethodSource("createTravelerPositions")
    void canReturnTheCorrectSegmentCoordinates(TravellerPosition segmentPosition) {
        TripSegment tripSegment = ManageLegTraversal.getSegmentFromTime(
            segmentPosition.start,
            segmentPosition.currentTime,
            segmentPosition.tripSegments
        );
        assertNotNull(tripSegment);
        assertEquals(segmentPosition.coordinates, tripSegment.start);
    }

    private static Stream<TravellerPosition> createTravelerPositions() {
        Instant segmentStartTime = ZonedDateTime.now().toInstant();
        List<TripSegment> tripSegments = createSegmentsForLeg();

        return Stream.of(
            new TravellerPosition(
                tripSegments.get(0).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(5),
                tripSegments
            ),
            new TravellerPosition(
                tripSegments.get(1).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(15),
                tripSegments
            ),
            new TravellerPosition(
                tripSegments.get(2).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(25),
                tripSegments
            ),
            new TravellerPosition(
                tripSegments.get(3).start,
                segmentStartTime,
                segmentStartTime.plusSeconds(35),
                tripSegments
            )
        );
    }

    private static class TravellerPosition {

        public Coordinates coordinates;

        public Instant start;

        public Instant currentTime;

        List<TripSegment> tripSegments;

        public TravellerPosition(
            Coordinates coordinates,
            Instant start,
            Instant currentTime,
            List<TripSegment> tripSegments
        ) {
            this.coordinates = coordinates;
            this.start = start;
            this.currentTime = currentTime;
            this.tripSegments = tripSegments;
        }
    }

    private static List<TripSegment> createSegmentsForLeg() {
        return interpolatePoints(busStopToJusticeCenterItinerary.legs.get(0));
    }

    private static String getDateTimeAsString(Date date, double offset) {
        Instant dateTime = date.toInstant().plusSeconds((long) offset);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault());;
        return formatter.format(dateTime);
    }
}
