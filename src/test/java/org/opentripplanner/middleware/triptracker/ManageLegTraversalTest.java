package org.opentripplanner.middleware.triptracker;

import io.leonard.PolylineUtils;
import io.leonard.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.triptracker.instruction.ContinueInstruction;
import org.opentripplanner.middleware.triptracker.instruction.DeviatedInstruction;
import org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.instruction.WaitForTransitInstruction;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSecondsToMilliseconds;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.interpolatePoints;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.getNextWayPoint;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isWithinExclusionZone;
import static org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction.TRIP_INSTRUCTION_END_OF_ROUTING;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.utils.GeometryUtils.calculateBearing;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

public class ManageLegTraversalTest {

    public static final int GMAP_UPCOMING_RADIUS = 30;
    private static Itinerary busStopToJusticeCenterItinerary;
    private static Itinerary edmundParkDriveToRockSpringsItinerary;

    private static Itinerary adairAvenueToMonroeDriveItinerary;
    private static Itinerary midtownToAnsleyItinerary;
    private static Itinerary midtownWalkItinerary;
    private static List<Place> midtownToAnsleyIntermediateStops;
    private static Itinerary firstLegBusTransit;
    private static Itinerary baptistChurchToEastCroganStreetIntinerary;
    private static Itinerary destinationAwayFromSidewalk;
    private static Itinerary walkGjacTo1js;

    private static final Locale locale = Locale.US;

    @BeforeAll
    public static void setUp() throws IOException {
        // Load default env.yml configuration.
        ConfigUtils.loadConfig(new String[]{});

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
        midtownToAnsleyItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/27nb-midtown-to-ansley.json"),
            Itinerary.class
        );
        midtownWalkItinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/midtown-walk.json"),
            Itinerary.class
        );
        firstLegBusTransit = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/first-leg-transit.json"),
            Itinerary.class
        );
        baptistChurchToEastCroganStreetIntinerary = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/baptist-church-to-east-crogan-street.json"),
            Itinerary.class
        );
        destinationAwayFromSidewalk = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/destination-away-from-sidewalk.json"),
            Itinerary.class
        );
        walkGjacTo1js = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-gjac-to-1js.json"),
            Itinerary.class
        );

        // Hold on to the original list of intermediate stops (some tests will overwrite it)
        midtownToAnsleyIntermediateStops = midtownToAnsleyItinerary.legs.get(1).intermediateStops;
    }

    @BeforeEach
    void beforeEach() {
        midtownToAnsleyItinerary.legs.get(1).intermediateStops = midtownToAnsleyIntermediateStops;
    }

    @ParameterizedTest
    @MethodSource("createTrace")
    void canTrackTrip(Instant instant, double lat, double lon, TripStatus expected, String message) {
        TrackedJourney trackedJourney = new TrackedJourney();
        TrackingLocation trackingLocation = new TrackingLocation(instant, lat, lon);
        trackedJourney.locations = List.of(trackingLocation);
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, busStopToJusticeCenterItinerary, new OtpUser());
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
        Instant startInstant = startTime.toInstant();
        long currentSegmentStartOffsetSecs = (long) Math.floor(current.cumulativeTime - current.timeInSegment);
        return Stream.of(
            Arguments.of(
                startInstant.plusSeconds((long) Math.floor(before.cumulativeTime - before.timeInSegment)),
                current.start.lat,
                current.start.lon,
                TripStatus.AHEAD_OF_SCHEDULE,
                "For the current location and time the traveler is ahead of schedule."
            ),
            Arguments.of(
                startInstant.plusSeconds(currentSegmentStartOffsetSecs),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE,
                "For the current location and time the traveler is on schedule."
            ),
            Arguments.of(
                startInstant.plusSeconds((long) Math.floor(after.cumulativeTime)),
                current.start.lat,
                current.start.lon,
                TripStatus.BEHIND_SCHEDULE,
                "For the current location and time the traveler is behind schedule."
            ),
            Arguments.of(
                startInstant.plusSeconds(currentSegmentStartOffsetSecs - 10),
                current.start.lat,
                current.start.lon,
                TripStatus.ON_SCHEDULE,
                "For the current location and time (with a slight deviation) the traveler is on schedule."
            ),
            Arguments.of(
                startInstant.plusSeconds((long) Math.floor(current.cumulativeTime)),
                current.start.lat + 1e-5,
                current.start.lon + 1e-5,
                TripStatus.ON_SCHEDULE,
                "The current location, with a slight deviation, is on schedule."
            ),
            Arguments.of(
                startInstant,
                notOnTripCoordinates.lat,
                notOnTripCoordinates.lon,
                TripStatus.DEVIATED,
                "Arbitrary lat/lon values which aren't on the trip leg."
            ),
            Arguments.of(
                busStopToJusticeCenterItinerary.endTime.toInstant().plusSeconds(1),
                deviatedCoordinates.lat,
                deviatedCoordinates.lon,
                TripStatus.DEVIATED,
                "Time which can not be attributed to a trip leg."
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createTurnByTurnTrace")
    void canTrackTurnByTurn(Leg firstLeg, TraceData traceData) {
        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(firstLeg)
            .setCurrentPosition(traceData.position)
            .setFirstLegOfTrip(firstLeg)
            .setCurrentTime(firstLeg.startTime.toInstant().minus(4, ChronoUnit.MINUTES))
            .setSpeed(0)
            .build();
        travelerPosition.locale = locale;
        TripInstruction tripInstruction = TravelerLocator.getInstruction(traceData.tripStatus, travelerPosition, traceData.isStartOfTrip);
        assertEquals(traceData.expectedInstruction, tripInstruction != null ? tripInstruction.build() : NO_INSTRUCTION, traceData.message);
    }

    private static Stream<Arguments> createTurnByTurnTrace() {
        final int NORTH_BEARING = 0;
        final int NORTH_EAST_BEARING = 45;
        final int SOUTH_BEARING = 180;
        final int SOUTH_WEST_BEARING = 225;
        final int NORTH_WEST_BEARING = 315;

        Leg adairAvenueToMonroeDriveLeg = adairAvenueToMonroeDriveItinerary.legs.get(0);
        List<Step> walkSteps = adairAvenueToMonroeDriveLeg.steps;
        String destinationName = adairAvenueToMonroeDriveLeg.to.name;

        Leg walkLeg = adairAvenueToMonroeDriveItinerary.legs.get(0);

        Step adairAvenueNortheastStep = walkSteps.get(0);
        Step virginiaCircleNortheastStep = walkSteps.get(1);
        Step ponceDeLeonPlaceNortheastStep = walkSteps.get(2);
        Step virginiaAvenueNortheastStep = walkSteps.get(5);
        Step kanugaStreetStep = walkSteps.get(7);

        Coordinates originCoords = new Coordinates(adairAvenueToMonroeDriveLeg.from);
        Coordinates destinationCoords = new Coordinates(adairAvenueToMonroeDriveLeg.to);
        Coordinates adairAvenueNortheastCoords = new Coordinates(adairAvenueNortheastStep);
        Coordinates midtownWalkCoords = new Coordinates(33.784372, -84.381410);
        Coordinates virginiaCircleNortheastCoords = new Coordinates(virginiaCircleNortheastStep);
        Coordinates ponceDeLeonPlaceNortheastCoords = new Coordinates(ponceDeLeonPlaceNortheastStep);
        Coordinates virginiaAvenuePoint = new Coordinates(virginiaAvenueNortheastStep);
        Coordinates pointBeforeTurn = new Coordinates(33.78151,-84.36481);
        Coordinates pointAfterTurn = new Coordinates(33.78165, -84.36484);
        Coordinates pointOnKanugaStreet = new Coordinates(33.781544, -84.367849);

        Leg firstBusLeg = firstLegBusTransit.legs.get(0);
        Coordinates busStopCoords = new Coordinates(firstBusLeg.from);
        String busStopName = firstBusLeg.from.name;

        Leg toEastCroganFirstLeg = baptistChurchToEastCroganStreetIntinerary.legs.get(0);
        Step southClaytonSt = toEastCroganFirstLeg.steps.get(1);
        Step eastCroganSt = toEastCroganFirstLeg.steps.get(2);
        Coordinates pointOnSouthClaytonSt = new Coordinates(33.955561, -83.988204);

        Leg legToDestinationAwayFromSidewalk = destinationAwayFromSidewalk.legs.get(0);
        Coordinates pointNearEndOfSidewalk = new Coordinates(33.958954, -84.006451);

        Leg midtownWalkLeg = midtownWalkItinerary.legs.get(0);
        Step midtownWalkFirstStep = midtownWalkLeg.steps.get(0);

        return Stream.of(
            Arguments.of(
                firstBusLeg,
                new TraceData(
                    TripStatus.DEVIATED,
                    createPoint(busStopCoords, 12, NORTH_WEST_BEARING),
                    new DeviatedInstruction(busStopName, locale),
                    true,
                    "Deviated from the start of a trip which starts with a bus leg. Suggest path to head towards."
                )
            ),
            Arguments.of(
                firstBusLeg,
                new TraceData(
                    TripStatus.ON_SCHEDULE,
                    createPoint(busStopCoords, 4, NORTH_WEST_BEARING),
                    new WaitForTransitInstruction(firstBusLeg, firstBusLeg.startTime.toInstant().minus(4, ChronoUnit.MINUTES), locale),
                    true,
                    "On-time and near the initial bus stop on trip which starts with a bus leg. Instructs to wait for bus."
                )
            ),
            Arguments.of(
                firstBusLeg,
                new TraceData(
                    TripStatus.ON_SCHEDULE,
                    new Coordinates(33.916779, -84.226556),
                    NO_INSTRUCTION,
                    true,
                    "On transit leg away from the boarding location (or walked past the bus stop). No specific instruction to give."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    originCoords,
                    new OnTrackInstruction(10, adairAvenueNortheastStep, locale),
                    true,
                    "Just started the trip and near to the instruction for the first step. "
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    originCoords,
                    new OnTrackInstruction(10, adairAvenueNortheastStep, locale),
                    false,
                    "Coming up on first instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    adairAvenueNortheastCoords,
                    new OnTrackInstruction(2, adairAvenueNortheastStep, locale),
                    false,
                    "On first instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    TripStatus.DEVIATED,
                    createPoint(adairAvenueNortheastCoords, 12, NORTH_WEST_BEARING),
                    new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale),
                    false,
                    "Deviated to the north of east to west path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    TripStatus.DEVIATED,
                    createPoint(adairAvenueNortheastCoords, 12, SOUTH_WEST_BEARING),
                    new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale),
                    false,
                    "Deviated to the south of east to west path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    createPoint(virginiaCircleNortheastCoords, 12, SOUTH_WEST_BEARING),
                    new ContinueInstruction(virginiaCircleNortheastStep, locale),
                    false,
                    "On track approaching second step, provide continue instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    TripStatus.DEVIATED,
                    createPoint(virginiaCircleNortheastCoords, 8, NORTH_BEARING),
                    new OnTrackInstruction(9, virginiaCircleNortheastStep, locale),
                    false,
                    "Deviated from path, but within the upcoming radius of second instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    virginiaCircleNortheastCoords,
                    new OnTrackInstruction(0, virginiaCircleNortheastStep, locale),
                    false,
                    "On second instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    TripStatus.DEVIATED,
                    createPoint(ponceDeLeonPlaceNortheastCoords, 10, NORTH_WEST_BEARING),
                    new DeviatedInstruction(ponceDeLeonPlaceNortheastStep.streetName, locale),
                    false,
                    "Deviated to the west of south to north path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    TripStatus.DEVIATED,
                    createPoint(ponceDeLeonPlaceNortheastCoords, 10, NORTH_EAST_BEARING),
                    new DeviatedInstruction(ponceDeLeonPlaceNortheastStep.streetName, locale),
                    false,
                    "Deviated to the east of south to north path. Suggest path to head towards."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    createPoint(pointBeforeTurn, 8, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)),
                    new OnTrackInstruction(10, virginiaAvenueNortheastStep, locale),
                    false,
                    "Approaching left turn on Virginia Avenue (Test to make sure turn is not missed)."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    createPoint(pointBeforeTurn, 17, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)),
                    new OnTrackInstruction(2, virginiaAvenueNortheastStep, locale),
                    false,
                    "Turn left on to Virginia Avenue (Test to make sure turn is not missed)."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    createPoint(pointAfterTurn, 0, calculateBearing(pointAfterTurn, virginiaAvenuePoint)),
                    new ContinueInstruction(virginiaAvenueNortheastStep, locale),
                    false,
                    "After turn left on to Virginia Avenue should provide continue instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    createPoint(pointOnKanugaStreet, 0, NORTH_WEST_BEARING),
                    new ContinueInstruction(kanugaStreetStep, locale),
                    false,
                    "After final turn on to Kanuga Street should provide continue instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    createPoint(destinationCoords, 8, SOUTH_BEARING),
                    TRIP_INSTRUCTION_END_OF_ROUTING, // new OnTrackInstruction(10, destinationName, locale),
                    false,
                    "Coming up on destination instruction."
                )
            ),
            Arguments.of(
                walkLeg,
                new TraceData(
                    destinationCoords,
                    TRIP_INSTRUCTION_END_OF_ROUTING, // new OnTrackInstruction(2, destinationName, locale),
                    false,
                    "On destination instruction."
                )
            ),
            Arguments.of(
                toEastCroganFirstLeg,
                new TraceData(
                    pointOnSouthClaytonSt,
                    new ContinueInstruction(southClaytonSt, locale),
                    false,
                    "On track passed second step and not near to next step, provide continue instruction for second step."
                )
            ),
            Arguments.of(
                toEastCroganFirstLeg,
                new TraceData(
                    createPoint(pointOnSouthClaytonSt, 12, NORTH_WEST_BEARING),
                    new ContinueInstruction(southClaytonSt, locale),
                    false,
                    "On track a bit near to the next step, provide continue instruction for second step."
                )
            ),
            Arguments.of(
                toEastCroganFirstLeg,
                new TraceData(
                    createPoint(pointOnSouthClaytonSt, 72, NORTH_BEARING),
                    new ContinueInstruction(eastCroganSt, locale),
                    false,
                    "On track passed next step, provide continue instruction for next step."
                )
            ),
            Arguments.of(
                legToDestinationAwayFromSidewalk,
                new TraceData(
                    pointNearEndOfSidewalk,
                     TRIP_INSTRUCTION_END_OF_ROUTING,
                    "Provide instruction for reaching destination if it is not near end of shape of walk leg."
                )
            ),
            Arguments.of(
                midtownWalkLeg,
                new TraceData(
                    midtownWalkCoords,
                    new ContinueInstruction(midtownWalkFirstStep, locale),
                    false,
                    "Immediately after departure instruction. Should provide a 'Continue' instruction and not 'No instruction'."
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createTransitRideTrace")
    void canTrackTransitRide(TraceData traceData) {
        Itinerary itinerary = midtownToAnsleyItinerary;
        Leg transitLeg = itinerary.legs.get(1);

        // In some cases, simulate missing intermediateStops field from OTP. Tests should still run to end.
        if (traceData.dismissIntermediateStops) {
            transitLeg.intermediateStops = null;
        }

        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(transitLeg)
            .setCurrentPosition(traceData.position)
            // Unless specified in traceData, an instant corresponding to approx the trip start time should be provided
            // for correct instructions to be produced.
            .setCurrentTime(traceData.instant != null ? traceData.instant : transitLeg.startTime.toInstant())
            .setSpeed(traceData.speed)
            .build();
        travelerPosition.locale = locale;
        TripInstruction tripInstruction = TravelerLocator.getInstruction(traceData.tripStatus, travelerPosition, false);
        assertEquals(traceData.expectedInstruction, tripInstruction != null ? tripInstruction.build() : NO_INSTRUCTION, traceData.message);
    }

    private static Stream<Arguments> createTransitRideTrace() {
        final int SOUTH_WEST_BEARING = 225;
        Leg transitLeg = midtownToAnsleyItinerary.legs.get(1);
        String destinationName = transitLeg.to.name;

        Coordinates originCoords = new Coordinates(transitLeg.from);
        Coordinates destinationCoords = new Coordinates(transitLeg.to);

        return Stream.of(
            Arguments.of(
                new TraceData(
                    originCoords,
                    NO_INSTRUCTION,
                    Instant.now(),
                    "If present at the transit stop after the trip departure, there should not be an instruction."
                )
            ),
            Arguments.of(
                new TraceData(
                    originCoords,
                    new WaitForTransitInstruction(transitLeg, transitLeg.startTime.toInstant(), locale).build(),
                    "At the bus stop, or just boarded the transit vehicle leg, it should still tell the user to board the bus."
                )
            ),
            // This instruction can be missed if the transit vehicle is in a slow/congested area
            // with speeds less than 5 meters/second (11.1 mph, 18 km/h).
            Arguments.of(
                new TraceData(
                    new Coordinates(33.78647, -84.38041),
                    6, // meters per second, ~13.4 mph or 21.6 km/h. The threshold is 5 meters per second.
                    String.format("Ride 4 min / 8 stops to %s", destinationName),
                    "Summarize the transit trip as vehicle departs."
                )
            ),
            Arguments.of(
                new TraceData(
                    new Coordinates(33.78792, -84.37776),
                    NO_INSTRUCTION,
                    "On the transit segment, but far from the arrival stop, so no instruction is given."
                )
            ),
            Arguments.of(
                new TraceData(
                    new Coordinates(33.79139, -84.37441),
                    String.format("Your stop is coming up (%s)", destinationName),
                    "Upcoming arrival stop instruction."
                )
            ),
            Arguments.of(
                new TraceData(
                    new Coordinates(33.79362, -84.37235),
                    String.format("Your stop is coming up (%s)", destinationName),
                    "Between the third and second to last stop."
                )
            ),
            Arguments.of(
                new TraceData(
                    new Coordinates(33.79445, -84.37156),
                    String.format("Get off at next stop (%s)", destinationName),
                    "One-stop warning (only within 'upcoming' distance of that stop) before the stop to get off"
                )
            ),
            Arguments.of(
                new TraceData(
                    new Coordinates(33.79478, -84.37127),
                    String.format("Get off at next stop (%s)", destinationName),
                    "Past the one-stop warning from the stop where you should get off.",
                    true
                )
            ),
            Arguments.of(
                new TraceData(
                    new Coordinates(33.79489, -84.37115),
                    String.format("Get off at next stop (%s)", destinationName),
                    "Past the one-stop warning from the stop where you should get off (#2)."
                )
            ),
            Arguments.of(
                new TraceData(
                    createPoint(destinationCoords, 8, SOUTH_WEST_BEARING),
                    String.format("Get off here (%s)", destinationName),
                    "Instruction approaching or at the stop where you should get off."
                )
            ),
            Arguments.of(
                new TraceData(
                    TripStatus.DEVIATED,
                    new Coordinates(33.79371, -84.37711),
                    NO_INSTRUCTION,
                    "No instruction provided besides trip status if bus is deviated or user missed their stop."
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createGetNearestWaypointTrace")
    void canGetNearestWaypoint(Step expectedStep, int startIndex, String message) {
        Leg leg = edmundParkDriveToRockSpringsItinerary.legs.get(0);
        List<Coordinates> allPositions = TravelerLocator.injectWaypointsIntoLegPositions(leg, leg.steps, TRIP_INSTRUCTION_UPCOMING_RADIUS);
        assertEquals(expectedStep, getNextWayPoint(allPositions, leg.steps, startIndex), message);
    }

    private static Stream<Arguments> createGetNearestWaypointTrace() {
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

    @ParameterizedTest
    @MethodSource("createCanInjectWaypointsCases")
    void canInjectWaypoints(Leg leg, int radius) {
        final int PRECISION_DIGITS = 5;
        final double DELTA = 1e-5;

        List<Position> legPositions = PolylineUtils.decode(leg.legGeometry.points, PRECISION_DIGITS);
        int excluded = getNumberOfExcludedPoints(legPositions, leg, radius);
        int expectedNumberOfPositions = (legPositions.size() - excluded) + leg.steps.size() + 2; // from and to points.
        List<Coordinates> allPositions = TravelerLocator.injectWaypointsIntoLegPositions(leg, leg.steps, radius);
        assertEquals(expectedNumberOfPositions, allPositions.size());
        Coordinates lastPosition = allPositions.get(allPositions.size() - 1);
        assertEquals(leg.to.lat, lastPosition.lat, DELTA);
        assertEquals(leg.to.lon, lastPosition.lon, DELTA);

        // If the last leg position is the same as the destination point, (at given precision)
        // then skip the check because the second to last waypoint will not be related to the last leg position,
        if (Math.abs(leg.to.lat - lastPosition.lat) > DELTA || Math.abs(leg.to.lon - lastPosition.lon) > DELTA) {
            Coordinates secondLastPosition = allPositions.get(allPositions.size() - 2);
            Position lastLegPosition = legPositions.get(legPositions.size() - 1);
            assertEquals(lastLegPosition.getLatitude(), secondLastPosition.lat, DELTA);
            assertEquals(lastLegPosition.getLongitude(), secondLastPosition.lon, DELTA);
        }
    }

    static Stream<Arguments> createCanInjectWaypointsCases() {
        return Stream.of(
            Arguments.of(edmundParkDriveToRockSpringsItinerary.legs.get(0), TRIP_INSTRUCTION_UPCOMING_RADIUS),
            Arguments.of(walkGjacTo1js.legs.get(0), GMAP_UPCOMING_RADIUS)
        );
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
                TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, busStopToJusticeCenterItinerary, null);
                TripStatus tripStatus = TripStatus.getTripStatus(travelerPosition);
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

    @Test
    void testGetDistanceToEndOfLeg() {
        // Case where distance to end of leg was previously incorrectly computed
        // (At end of routing for walk trip to One Justice Square)
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.locations = List.of(
            new TrackingLocation(Instant.now(), 33.95242212998748, -83.99714406536067)
        );
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkGjacTo1js, null);

        assertTrue(TravelerLocator.getDistanceToEndOfLeg(travelerPosition, GMAP_UPCOMING_RADIUS) <= GMAP_UPCOMING_RADIUS);
    }

    private static class TraceData {
        TripStatus tripStatus = TripStatus.ON_SCHEDULE;
        Coordinates position;
        int speed;
        String expectedInstruction;
        boolean isStartOfTrip;
        boolean dismissIntermediateStops;
        Instant instant;
        String message;

        public TraceData(Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
            this.position = position;
            this.expectedInstruction = expectedInstruction;
            this.isStartOfTrip = isStartOfTrip;
            this.message = message;
        }

        public TraceData(Coordinates position, TripInstruction expectedInstruction, boolean isStartOfTrip, String message) {
            this(position, expectedInstruction.build(), isStartOfTrip, message);
        }

        public TraceData(Coordinates position, String expectedInstruction, String message) {
            this(position, expectedInstruction, false, message);
        }

        public TraceData(Coordinates position, String expectedInstruction, Instant instant, String message) {
            this(position, expectedInstruction, false, message);
            this.instant = instant;
        }

        public TraceData(Coordinates position, String expectedInstruction, String message, boolean dismissIntermediateStops) {
            this(position, expectedInstruction, false, message);
            this.dismissIntermediateStops = dismissIntermediateStops;
        }

        public TraceData(Coordinates position, int speed, String expectedInstruction, String message) {
            this(position, expectedInstruction, false, message);
            this.speed = speed;
        }

        public TraceData(TripStatus tripStatus, Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
            this.tripStatus = tripStatus;
            this.position = position;
            this.expectedInstruction = expectedInstruction;
            this.isStartOfTrip = isStartOfTrip;
            this.message = message;
        }

        public TraceData(TripStatus tripStatus, Coordinates position, TripInstruction expectedInstruction, boolean isStartOfTrip, String message) {
            this(tripStatus, position, expectedInstruction.build(), isStartOfTrip, message);
        }

        public TraceData(TripStatus tripStatus, Coordinates position, String expectedInstruction, String message) {
            this(tripStatus, position, expectedInstruction, false, message);
        }
    }

    private static List<LegSegment> createSegmentsForLeg() {
        return interpolatePoints(busStopToJusticeCenterItinerary.legs.get(0));
    }

    private int getNumberOfExcludedPoints(List<Position> legPositions, Leg leg, int exclusionRadius) {
        int excluded = 0;
        for (Position position : legPositions) {
            if (position != legPositions.get(legPositions.size() - 2) && isWithinExclusionZone(new Coordinates(position), leg.steps, exclusionRadius)) {
                excluded++;
            }
        }
        if (isWithinExclusionZone(new Coordinates(leg.from), leg.steps, exclusionRadius)) {
            excluded++;
        }
        if (isWithinExclusionZone(new Coordinates(leg.to), leg.steps, exclusionRadius)) {
            excluded++;
        }
        return excluded;
    }
}
