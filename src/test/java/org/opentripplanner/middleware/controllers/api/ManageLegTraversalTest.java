package org.opentripplanner.middleware.controllers.api;

import io.leonard.PolylineUtils;
import io.leonard.Position;
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
import org.opentripplanner.middleware.triptracker.TripInstruction;
import org.opentripplanner.middleware.triptracker.LegSegment;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TravelerLocator;
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
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSecondsToMilliseconds;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.interpolatePoints;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.getNextStep;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.injectStepsIntoLegPositions;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isWithinExclusionZone;
import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.utils.GeometryUtils.calculateBearing;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

public class ManageLegTraversalTest {

    private static Itinerary busStopToJusticeCenterItinerary;
    private static Itinerary edmundParkDriveToRockSpringsItinerary;

    private static Itinerary adairAvenueToMonroeDriveItinerary;

    @BeforeAll
    public static void setUp() throws IOException {
        busStopToJusticeCenterItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/bus-stop-justice-center-trip.json"),
            Itinerary.class
        );
        edmundParkDriveToRockSpringsItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/edmund-park-drive-to-rock-springs.json"),
            Itinerary.class
        );
        adairAvenueToMonroeDriveItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/adair-avenue-to-monroe-drive.json"),
            Itinerary.class
        );
    }

    @ParameterizedTest
    @MethodSource("createTrace")
    void canTrackTrip(String time, double lat, double lon, TripStatus expected, String message) {
        TrackedJourney trackedJourney = new TrackedJourney();
        TrackingLocation trackingLocation = new TrackingLocation(time, lat, lon);
        trackedJourney.locations = List.of(trackingLocation);
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, busStopToJusticeCenterItinerary);
        TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
        assertEquals(expected, tripStatus, message);
    }

    private static Stream<Arguments> createTrace() {
        Date startTime = busStopToJusticeCenterItinerary.startTime;
        List<LegSegment> legSegments = createSegmentsForLeg();
        LegSegment before = legSegments.get(8);
        LegSegment current = legSegments.get(10);
        LegSegment after = legSegments.get(12);
        Coordinates deviatedCoordinates = createPoint(
            current.start,
            90,
            calculateBearing(current.start, after.start)
        );
        Coordinates notOnTripCoordinates = createPoint(
            current.start,
            1000,
            calculateBearing(current.start, after.start)
        );
        return Stream.of(
            Arguments.of(
                getDateTimeAsString(startTime, before.cumulativeTime - before.timeInSegment),
                current.start.lat,
                current.start.lon,
                TripStatus.AHEAD_OF_SCHEDULE,
                "For the current location and time the traveler is ahead of schedule."
            ),
            Arguments.of(
                getDateTimeAsString(startTime, current.cumulativeTime - current.timeInSegment),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE,
                "For the current location and time the traveler is on schedule."
            ),
            Arguments.of(
                getDateTimeAsString(startTime, after.cumulativeTime),
                current.start.lat,
                current.start.lon,
                TripStatus.BEHIND_SCHEDULE,
                "For the current location and time the traveler is behind schedule."
            ),
            Arguments.of(
                getDateTimeAsString(startTime, (current.cumulativeTime - current.timeInSegment) - 10),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE,
                "For the current location and time (with a slight deviation) the traveler is on schedule."
            ),
            Arguments.of(
                getDateTimeAsString(startTime, current.cumulativeTime),
                current.start.lat + 0.00001,
                current.start.lon + 0.00001,
                TripStatus.ON_SCHEDULE,
                "The current location, with a slight deviation, is on schedule."
            ),
            Arguments.of(
                getDateTimeAsString(startTime, 0),
                notOnTripCoordinates.lat,
                notOnTripCoordinates.lon,
                TripStatus.DEVIATED,
                "Arbitrary lat/lon values which aren't on the trip leg."
            ),
            Arguments.of(
                getDateTimeAsString(busStopToJusticeCenterItinerary.endTime, 1),
                deviatedCoordinates.lat,
                deviatedCoordinates.lon,
                TripStatus.DEVIATED,
                "Time which can not be attributed to a trip leg."
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createTurnByTurnTrace")
    void canTrackTurnByTurn(TurnTrace turnTrace) {
        TravelerPosition travelerPosition = new TravelerPosition(turnTrace.itinerary.legs.get(0), turnTrace.position);
        String tripInstruction = TravelerLocator.getInstruction(turnTrace.tripStatus, travelerPosition, turnTrace.isStartOfTrip);
        assertEquals(turnTrace.expectedInstruction, Objects.requireNonNullElse(tripInstruction, NO_INSTRUCTION), turnTrace.message);
    }

    private static Stream<Arguments> createTurnByTurnTrace() {
        final int NORTH_BEARING = 0;
        final int NORTH_EAST_BEARING = 45;
        final int SOUTH_BEARING = 180;
        final int SOUTH_WEST_BEARING = 225;
        final int NORTH_WEST_BEARING = 315;
        Leg adairAvenueToMonroeDriveLeg = adairAvenueToMonroeDriveItinerary.legs.get(0);
        List<Step> walkSteps = adairAvenueToMonroeDriveLeg.steps;
        Coordinates originCoords = new Coordinates(adairAvenueToMonroeDriveLeg.from);
        Coordinates destinationCoords = new Coordinates(adairAvenueToMonroeDriveLeg.to);
        String destinationName = adairAvenueToMonroeDriveLeg.to.name;
        Step stepOne = walkSteps.get(0);
        Coordinates stepOneCoords = new Coordinates(stepOne);
        Step stepTwo = walkSteps.get(1);
        Coordinates stepTwoCoords = new Coordinates(stepTwo);
        Step stepThree = walkSteps.get(2);
        Coordinates stepThreeCoords = new Coordinates(stepThree);

        Step virginiaAvenue = walkSteps.get(5);
        Coordinates pointBeforeTurn = new Coordinates(33.78151,-84.36481);
        Coordinates virginiaAvenuePoint = new Coordinates(virginiaAvenue);

        return Stream.of(
            Arguments.of(
                new TurnTrace(
                    createPoint(pointBeforeTurn, 8, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)),
                    new TripInstruction(10, virginiaAvenue).build(),
                    false,
                    "Approaching left turn on Virginia Avenue (Test to make sure turn is not missed)."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(pointBeforeTurn, 16, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)),
                    new TripInstruction(2, virginiaAvenue).build(),
                    false,
                    "Turn left on to Virginia Avenue (Test to make sure turn is not missed)."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    originCoords,
                    new TripInstruction(10, stepOne).build(),
                    true,
                    "Just started the trip and near to the instruction for the first step. "
                )
            ),
            Arguments.of(
                new TurnTrace(
                    originCoords,
                    new TripInstruction(10, stepOne).build(),
                    false,
                    "Coming up on first instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    stepOneCoords,
                    new TripInstruction(2, stepOne).build(),
                    false,
                    "On first instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    createPoint(stepOneCoords, 12, NORTH_WEST_BEARING),
                    new TripInstruction(stepOne.streetName).build(),
                    false,
                    "Deviated to the north of east to west path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    createPoint(stepOneCoords, 12, SOUTH_WEST_BEARING),
                    new TripInstruction(stepOne.streetName).build(),
                    false,
                    "Deviated to the south of east to west path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(stepTwoCoords, 12, SOUTH_WEST_BEARING),
                    NO_INSTRUCTION,
                    false,
                    "On track approaching second step, but not close enough for instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    createPoint(stepTwoCoords, 8, NORTH_BEARING),
                    new TripInstruction(9, stepTwo).build(),
                    false,
                    "Deviated from path, but within the upcoming radius of second instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    stepTwoCoords,
                    new TripInstruction(0, stepTwo).build(),
                    false,
                    "On second instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    createPoint(stepThreeCoords, 8, NORTH_WEST_BEARING),
                    new TripInstruction(10, stepThree).build(),
                    false,
                    "Deviated to the west of south to north path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    createPoint(stepThreeCoords, 8, NORTH_EAST_BEARING),
                    new TripInstruction(10, stepThree).build(),
                    false,
                    "Deviated to the east of south to north path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(destinationCoords, 8, SOUTH_BEARING),
                    new TripInstruction(10, destinationName).build(),
                    false,
                    "Coming up on destination instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    destinationCoords,
                    new TripInstruction(2, destinationName).build(),
                    false,
                    "On destination instruction."
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createGetNearestStepTrace")
    void canGetNearestStep(Step expectedStep, int startIndex, String message) {
        Leg leg = edmundParkDriveToRockSpringsItinerary.legs.get(0);
        List<Coordinates> allPositions = injectStepsIntoLegPositions(edmundParkDriveToRockSpringsItinerary.legs.get(0));
        assertEquals(expectedStep, getNextStep(leg, allPositions, startIndex), message);
    }

    private static Stream<Arguments> createGetNearestStepTrace() {
        Leg leg = edmundParkDriveToRockSpringsItinerary.legs.get(0);
        return Stream.of(
            Arguments.of(leg.steps.get(0), 0, "At the beginning, expecting the first step."),
            Arguments.of(leg.steps.get(0), 0, "On first step."),
            Arguments.of(leg.steps.get(1), 2, "Approaching second step, expecting the second step."),
            Arguments.of(leg.steps.get(1), 3, "Approaching second step, expecting the second step."),
            Arguments.of(leg.steps.get(1), 4, "Approaching second step, expecting the second step."),
            Arguments.of(leg.steps.get(1), 5, "Approaching second step, expecting the second step."),
            Arguments.of(leg.steps.get(2), 7, "Approaching third step, expecting the third step."),
            Arguments.of(leg.steps.get(3), 9, "After third step, expecting the fourth step."),
            Arguments.of(null, 10, "After fourth and final step, expecting no step.")
        );
    }

    @Test
    void canInjectSteps() {
        Leg leg = edmundParkDriveToRockSpringsItinerary.legs.get(0);
        List<Position> legPositions = PolylineUtils.decode(leg.legGeometry.points, 5);
        int excluded = getNumberOfExcludedPoints(legPositions, leg);
        int expectedNumberOfPositions = (legPositions.size() - excluded) + leg.steps.size() + 2; // from and to points.
        List<Coordinates> allPositions = injectStepsIntoLegPositions(leg);
        assertEquals(expectedNumberOfPositions, allPositions.size());
    }

    @Test
    void canAccumulateCorrectStartAndEndCoordinates() {
        List<LegSegment> legSegments = createSegmentsForLeg();
        for (int i = 0; i < legSegments.size() - 1; i++) {
            LegSegment legSegmentOne = legSegments.get(i);
            LegSegment legSegmentTwo = legSegments.get(i + 1);
            assertEquals(legSegmentOne.end.lat, legSegmentTwo.start.lat);
        }
    }

    @Test
    void canTrackLegWithoutDeviating() {
        for (int legIndex = 0; legIndex < busStopToJusticeCenterItinerary.legs.size(); legIndex++) {
            List<LegSegment> legSegments = createSegmentsForLeg();
            TrackedJourney trackedJourney = new TrackedJourney();
            ZonedDateTime startOfTrip = ZonedDateTime.ofInstant(
                busStopToJusticeCenterItinerary.legs.get(legIndex).startTime.toInstant(),
                DateTimeUtils.getOtpZoneId()
            );

            ZonedDateTime currentTime = startOfTrip;
            double cumulativeTravelTime = 0;
            for (LegSegment legSegment : legSegments) {
                trackedJourney.locations = List.of(
                    new TrackingLocation(
                        legSegment.start.lat,
                        legSegment.start.lon,
                        new Date(currentTime.toInstant().toEpochMilli())
                    )
                );
                TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, busStopToJusticeCenterItinerary);
                TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
                System.out.println(tripStatus.name());
                assertEquals(TripStatus.ON_SCHEDULE.name(), tripStatus.name());
                cumulativeTravelTime += legSegment.timeInSegment;
                currentTime = startOfTrip.plus(
                    getSecondsToMilliseconds(cumulativeTravelTime) - 1000,
                    ChronoUnit.MILLIS
                );
            }
        }
    }

    @Test
    void cumulativeSegmentTimeMatchesWalkLegDuration() {
        List<LegSegment> legSegments = createSegmentsForLeg();
        double cumulative = 0;
        for (LegSegment legSegment : legSegments) {
            cumulative += legSegment.timeInSegment;
        }
        assertEquals(busStopToJusticeCenterItinerary.legs.get(0).duration, cumulative, 0.01f);
    }

    private static class TurnTrace {
        Itinerary itinerary = adairAvenueToMonroeDriveItinerary;
        TripStatus tripStatus = TripStatus.ON_SCHEDULE;
        Coordinates position;
        String expectedInstruction;
        boolean isStartOfTrip;
        String message;

        public TurnTrace(Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
            this.position = position;
            this.expectedInstruction = expectedInstruction;
            this.isStartOfTrip = isStartOfTrip;
            this.message = message;
        }

        public TurnTrace(TripStatus tripStatus, Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
            this.tripStatus = tripStatus;
            this.position = position;
            this.expectedInstruction = expectedInstruction;
            this.isStartOfTrip = isStartOfTrip;
            this.message = message;
        }
    }

    private static List<LegSegment> createSegmentsForLeg() {
        return interpolatePoints(busStopToJusticeCenterItinerary.legs.get(0));
    }

    private static String getDateTimeAsString(Date date, double offset) {
        Instant dateTime = date.toInstant().plusSeconds((long) offset);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault());;
        return formatter.format(dateTime);
    }

    private int getNumberOfExcludedPoints(List<Position> legPositions, Leg leg) {
        int excluded = 0;
        for (Position position : legPositions) {
            if (isWithinExclusionZone(new Coordinates(position), leg.steps)) {
                excluded++;
            }
        }
        if (isWithinExclusionZone(new Coordinates(leg.from), leg.steps)) {
            excluded++;
        }
        if (isWithinExclusionZone(new Coordinates(leg.to), leg.steps)) {
            excluded++;
        }
        return excluded;
    }
}
