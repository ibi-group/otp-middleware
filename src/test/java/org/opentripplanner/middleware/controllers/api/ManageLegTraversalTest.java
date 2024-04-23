package org.opentripplanner.middleware.controllers.api;

import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.checkerframework.checker.units.qual.C;
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
import org.opentripplanner.middleware.triptracker.ManageLegTraversal;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            new Coordinates(current.start.lat, current.start.lon),
            90,
            calculateBearing(
                new Coordinates(current.start.lat, current.start.lon),
                new Coordinates(after.start.lat, after.start.lon)
            )
        );
        Coordinates notOnTripCoordinates = createPoint(
            new Coordinates(current.start.lat, current.start.lon),
            1000,
            calculateBearing(
                new Coordinates(current.start.lat, current.start.lon),
                new Coordinates(after.start.lat, after.start.lon)
            )
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
                TripStatus.NO_STATUS,
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
    void canTrackBusStopToJusticeCenterTurnByTurn(TurnTrace turnTrace) {
        TravelerPosition travelerPosition = new TravelerPosition(turnTrace.itinerary.legs.get(0), turnTrace.position);
        String tripInstruction = TravelerLocator.getInstruction(turnTrace.tripStatus, travelerPosition, turnTrace.isStartOfTrip);
        assertEquals(turnTrace.expectedInstruction, Objects.requireNonNullElse(tripInstruction, NO_INSTRUCTION), turnTrace.message);
    }

    private static Stream<Arguments> createTurnByTurnTrace() {
        Leg justiceCenterLeg = busStopToJusticeCenterItinerary.legs.get(0);
        List<Step> walkSteps = justiceCenterLeg.steps;
        Coordinates originCoords = new Coordinates(justiceCenterLeg.from);
        Coordinates destinationCoords = new Coordinates(justiceCenterLeg.to);
        String destinationName = justiceCenterLeg.to.name;
        Step stepOne = walkSteps.get(0);
        Coordinates stepOneCoords = new Coordinates(stepOne);
        Step stepTwo = walkSteps.get(1);
        Coordinates stepTwoCoords = new Coordinates(stepTwo);
        Step stepThree = walkSteps.get(2);
        Coordinates stepThreeCoords = new Coordinates(stepThree);
        Step stepFour = walkSteps.get(3);
        Coordinates stepFourCoords = new Coordinates(stepFour);
        Step stepFive = walkSteps.get(4);
        Coordinates stepFiveCoords = new Coordinates(stepFive);

        Leg rockSpringsLeg = edmundParkDriveToRockSpringsItinerary.legs.get(0);
        List<Step> rockSpringsSteps = rockSpringsLeg.steps;

        Coordinates fourthGeoPoint = new Coordinates(33.79352,-84.34148);
        Coordinates sixthGeoPoint = new Coordinates(33.79332,-84.34099);
        Step rockSpringsStepTwo = rockSpringsSteps.get(1);

        Leg adairAvenueToMonroeDriveLeg = adairAvenueToMonroeDriveItinerary.legs.get(0);
        List<Step> adairAvenueToMonroeDriveLegSteps = adairAvenueToMonroeDriveLeg.steps;
        Step virginiaAvenue = adairAvenueToMonroeDriveLegSteps.get(5);

        Coordinates pointBeforeTurn = new Coordinates(33.78151,-84.36481);
        Coordinates virginiaAvenuePoint = new Coordinates(virginiaAvenue);

        return Stream.of(
            Arguments.of(
                new TurnTrace(
                    adairAvenueToMonroeDriveItinerary,
                    createPoint(pointBeforeTurn, 8, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)),
                    new TripInstruction(10, virginiaAvenue).build(),
                    false,
                    "Approaching left turn on Virginia Avenue."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    adairAvenueToMonroeDriveItinerary,
                    createPoint(pointBeforeTurn, 16, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)),
                    new TripInstruction(2, virginiaAvenue).build(),
                    false,
                    "Turn left on to Virginia Avenue."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    edmundParkDriveToRockSpringsItinerary,
                    fourthGeoPoint,
                    NO_INSTRUCTION,
                    false,
                    "Approaching second step, but not close enough for instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    edmundParkDriveToRockSpringsItinerary,
                    createPoint(sixthGeoPoint, 45, calculateBearing(sixthGeoPoint, new Coordinates(rockSpringsStepTwo))),
                    new TripInstruction(10, rockSpringsStepTwo).build(),
                    false,
                    "Upcoming second step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    edmundParkDriveToRockSpringsItinerary,
                    createPoint(sixthGeoPoint, 52, calculateBearing(sixthGeoPoint, new Coordinates(rockSpringsStepTwo))),
                    new TripInstruction(0, rockSpringsStepTwo).build(),
                    false,
                    "Immediate second step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    edmundParkDriveToRockSpringsItinerary,
                    sixthGeoPoint,
                    NO_INSTRUCTION,
                    false,
                    "Approaching second step, but not close enough for instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    stepTwoCoords,
                    new TripInstruction(0, stepTwo).build(),
                    false,
                    "Approach the instruction for the second step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    stepThreeCoords,
                    new TripInstruction(0, stepThree).build(),
                    false,
                    "Approach the instruction for the third step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    stepFourCoords,
                    new TripInstruction(0, stepFour).build(),
                    false,
                    "Approach the instruction for the fourth step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    stepFiveCoords,
                    new TripInstruction(0, stepFive).build(),
                    false,
                    "Approach the instruction for the fifth step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    originCoords,
                    new TripInstruction(1, stepOne).build(),
                    false,
                    "Deviated from trip, but is actually on track and within instruction radius of first step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    originCoords,
                    new TripInstruction(1, stepOne).build(),
                    true,
                    "Just started the trip and near to the instruction for the first step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(originCoords, 1, calculateBearing(originCoords, stepOneCoords)),
                    new TripInstruction(1, stepOne).build(),
                    false,
                    "Already into trip and approaching the instruction for the first step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(stepOneCoords, 55, calculateBearing(stepOneCoords, stepTwoCoords)),
                    new TripInstruction(9, stepTwo).build(),
                    false,
                    "Passed the first step and approaching the instruction for the second step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(stepFiveCoords, 10, calculateBearing(stepFiveCoords, destinationCoords)),
                    NO_INSTRUCTION,
                    false,
                    "Passed the last step and therefore the last available instruction."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(stepFourCoords, 3, calculateBearing(stepFourCoords, stepFiveCoords)),
                    new TripInstruction(8, stepFive).build(),
                    false,
                    "Approaching the instruction for the last step."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    destinationCoords,
                    new TripInstruction(2, destinationName).build(),
                    false,
                    "Arrived at destination."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    createPoint(stepFiveCoords, 190, calculateBearing(stepFiveCoords, destinationCoords)),
                    new TripInstruction(9, destinationName).build(),
                    false,
                    "Approaching the destination."
                )
            ),
            Arguments.of(
                new TurnTrace(
                    TripStatus.DEVIATED,
                    createPoint(stepFiveCoords, 10, calculateBearing(stepFiveCoords, destinationCoords)),
                    new TripInstruction(stepFive.streetName).build(),
                    false,
                    "Deviated from trip around the fifth step."
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
        Itinerary itinerary = busStopToJusticeCenterItinerary;
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

        public TurnTrace(Itinerary itinerary, Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
            this.itinerary = itinerary;
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
