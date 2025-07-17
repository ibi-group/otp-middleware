package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.triptracker.instruction.ContinueInstruction;
import org.opentripplanner.middleware.triptracker.instruction.ContinueRidingTransitInstruction;
import org.opentripplanner.middleware.triptracker.instruction.DeviatedInstruction;
import org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.instruction.WaitForTransitInstruction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettBusOpNotificationMessage;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettNotifyBusOperator;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.getSecondsToMilliseconds;
import static org.opentripplanner.middleware.triptracker.ManageLegTraversal.interpolatePoints;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.getNextWayPoint;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.isWithinExclusionZone;
import static org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction.TRIP_INSTRUCTION_END_OF_ROUTING;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.utils.ConfigUtils.DEFAULT_ENV;
import static org.opentripplanner.middleware.utils.GeometryUtils.calculateBearing;
import static org.opentripplanner.middleware.utils.GeometryUtils.createPoint;

public class ManageLegTraversalTest extends OtpMiddlewareTestEnvironment {

    public static final int GMAP_UPCOMING_RADIUS = 30;
    public static final Coordinates WALK_AND_TRANSIT_LEG_OVERLAP_POINT = new Coordinates(33.90765017135988, -84.27299581343617);
    private static Itinerary busStopToJusticeCenterItinerary;
    private static Itinerary edmundParkDriveToRockSpringsItinerary;

    private static Itinerary adairAvenueToMonroeDriveItinerary;
    private static Itinerary midtownToAnsleyItinerary;
    private static Itinerary midtownWalkItinerary;
    private static List<Place> midtownToAnsleyIntermediateStops;
    private static Itinerary firstLegBusTransit;
    private static Itinerary baptistChurchToEastCroganStreetIntinerary;
    private static Itinerary arrivingOnBus40;
    private static Itinerary walkGjacTo1js;
    private static Itinerary walkToBusTransition;
    private static Itinerary walkToBus20;

    private static final Locale locale = Locale.US;

    @BeforeAll
    public static void setUp() throws IOException {
        // Load default env.yml configuration.
        ConfigUtils.loadConfig(DEFAULT_ENV);

        UsRideGwinnettNotifyBusOperator.IS_TEST = true;

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
        arrivingOnBus40 = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/bus-40-to-dest-away-from-sidewalk.json"),
            Itinerary.class
        );
        walkGjacTo1js = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-gjac-to-1js.json"),
            Itinerary.class
        );
        walkToBusTransition = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-transition.json"),
            Itinerary.class
        );
        walkToBus20 = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-20.json"),
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
    void canTrackTurnByTurn(String message, Leg firstLeg, TraceData traceData) {
        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(firstLeg)
            .setCurrentPosition(traceData.position)
            .setFirstLegOfTrip(firstLeg)
            .setCurrentTime(firstLeg.startTime.toInstant().minus(4, ChronoUnit.MINUTES))
            .setSpeed(0)
            .build();
        travelerPosition.locale = locale;
        TripInstruction tripInstruction = TravelerLocator.getInstruction(traceData.tripStatus, travelerPosition);
        assertEquals(traceData.expectedInstruction, tripInstruction != null ? tripInstruction.build() : NO_INSTRUCTION, message);
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

        Leg toEastCroganFirstLeg = baptistChurchToEastCroganStreetIntinerary.legs.get(0);
        Step southClaytonSt = toEastCroganFirstLeg.steps.get(1);
        Step eastCroganSt = toEastCroganFirstLeg.steps.get(2);
        Coordinates pointOnSouthClaytonSt = new Coordinates(33.955561, -83.988204);

        Leg legToDestinationAwayFromSidewalk = arrivingOnBus40.legs.get(2);
        Coordinates pointNearEndOfSidewalk = new Coordinates(33.958954, -84.006451);

        Leg midtownWalkLeg = midtownWalkItinerary.legs.get(0);
        Step midtownWalkFirstStep = midtownWalkLeg.steps.get(0);

        return Stream.of(
            Arguments.of(
                "Just started the trip and near to the instruction for the first step.",
                walkLeg,
                new TraceData()
                    .withPosition(originCoords)
                    .withExpectedInstruction(new OnTrackInstruction(10, adairAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "Coming up on first walk instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(originCoords)
                    .withExpectedInstruction(new OnTrackInstruction(10, adairAvenueNortheastStep, locale)),
                    false
            ),
            Arguments.of(
                "On first walk instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(adairAvenueNortheastCoords)
                    .withExpectedInstruction(new OnTrackInstruction(2, adairAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "Deviated to the north of east to west path. Suggest path to head towards.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(adairAvenueNortheastCoords, 12, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale))
            ),
            Arguments.of(
                "Deviated to the south of east to west path. Suggest path to head towards.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(adairAvenueNortheastCoords, 12, SOUTH_WEST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new DeviatedInstruction(adairAvenueNortheastStep.streetName, locale))
            ),
            Arguments.of(
                "On track approaching second step, provide continue instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(virginiaCircleNortheastCoords, 12, SOUTH_WEST_BEARING))
                    .withExpectedInstruction(new ContinueInstruction(virginiaCircleNortheastStep, locale))
            ),
            Arguments.of(
                "Deviated from path, but within the upcoming radius of second instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(virginiaCircleNortheastCoords, 8, NORTH_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new OnTrackInstruction(9, virginiaCircleNortheastStep, locale))
            ),
            Arguments.of(
                "On second walk instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(virginiaCircleNortheastCoords)
                    .withExpectedInstruction(new OnTrackInstruction(0, virginiaCircleNortheastStep, locale))
            ),
            Arguments.of(
                "Deviated to the west of south to north path. Suggest path to head towards.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(ponceDeLeonPlaceNortheastCoords, 10, NORTH_WEST_BEARING))
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(new DeviatedInstruction(ponceDeLeonPlaceNortheastStep.streetName, locale))
            ),
            Arguments.of(
                "Deviated to the east of south to north path. Suggest path to head towards.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(ponceDeLeonPlaceNortheastCoords, 10, NORTH_EAST_BEARING))
                    .withExpectedInstruction(new DeviatedInstruction(ponceDeLeonPlaceNortheastStep.streetName, locale))
                    .withTripStatus(TripStatus.DEVIATED)
            ),
            Arguments.of(
                "Approaching left turn on Virginia Avenue (Test to make sure turn is not missed).",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(pointBeforeTurn, 8, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)))
                    .withExpectedInstruction(new OnTrackInstruction(10, virginiaAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "Turn left on to Virginia Avenue (Test to make sure turn is not missed).",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(pointBeforeTurn, 17, calculateBearing(pointBeforeTurn, virginiaAvenuePoint)))
                    .withExpectedInstruction(new OnTrackInstruction(2, virginiaAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "After turn left on to Virginia Avenue should provide continue instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(pointAfterTurn, 0, calculateBearing(pointAfterTurn, virginiaAvenuePoint)))
                    .withExpectedInstruction(new ContinueInstruction(virginiaAvenueNortheastStep, locale))
            ),
            Arguments.of(
                "After final turn on to Kanuga Street should provide continue instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(pointOnKanugaStreet, 0, NORTH_WEST_BEARING))
                    .withExpectedInstruction(new ContinueInstruction(kanugaStreetStep, locale))
            ),
            Arguments.of(
                "Coming up on destination instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(createPoint(destinationCoords, 8, SOUTH_BEARING))
                    .withExpectedInstruction(new OnTrackInstruction(10, destinationName, locale))
            ),
            Arguments.of(
                "On destination instruction.",
                walkLeg,
                new TraceData()
                    .withPosition(destinationCoords)
                    .withExpectedInstruction(new OnTrackInstruction(2, destinationName, locale))
            ),
            Arguments.of(
                "On track passed second step and not near to next step, provide continue instruction for second step.",
                toEastCroganFirstLeg,
                new TraceData()
                    .withPosition(pointOnSouthClaytonSt)
                    .withExpectedInstruction(new ContinueInstruction(southClaytonSt, locale))
            ),
            Arguments.of(
                "On track a bit near to the next step, provide continue instruction for second step.",
                toEastCroganFirstLeg,
                new TraceData()
                    .withPosition(createPoint(pointOnSouthClaytonSt, 12, NORTH_WEST_BEARING))
                    .withExpectedInstruction(new ContinueInstruction(southClaytonSt, locale))
            ),
            Arguments.of(
                "On track passed next step, provide continue instruction for next step.",
                toEastCroganFirstLeg,
                new TraceData()
                    .withPosition(createPoint(pointOnSouthClaytonSt, 72, NORTH_BEARING))
                    .withExpectedInstruction(new ContinueInstruction(eastCroganSt, locale))
            ),
            Arguments.of(
                "Provide instruction for reaching destination if it is not near end of shape of walk leg.",
                legToDestinationAwayFromSidewalk,
                new TraceData()
                    .withPosition(pointNearEndOfSidewalk)
                    .withExpectedInstruction(TRIP_INSTRUCTION_END_OF_ROUTING)
            ),
            Arguments.of(
                "Immediately after departure instruction. Should provide a 'Continue' instruction and not 'No instruction'.",
                midtownWalkLeg,
                new TraceData()
                    .withPosition(midtownWalkCoords)
                    .withExpectedInstruction(new ContinueInstruction(midtownWalkFirstStep, locale))
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createBusStopTrace")
    void canTrackAtBusStop(String message, Itinerary itinerary, int currentLegIndex, TraceData traceData) throws JsonProcessingException {
        Leg currentLeg = itinerary.legs.get(currentLegIndex);
        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(currentLeg)
            .setCurrentPosition(traceData.position)
            .setFirstLegOfTrip(currentLeg)
            .setNextLeg(itinerary.legs.size() >= currentLegIndex + 2 ? itinerary.legs.get(currentLegIndex + 1) : null)
            .setCurrentTime(traceData.instant != null ? traceData.instant : currentLeg.startTime.toInstant().minus(4, ChronoUnit.MINUTES))
            .setSpeed(traceData.speed)
            .setTrackedJourney(new TrackedJourney())
            .build();
        travelerPosition.locale = locale;
        TripInstruction tripInstruction = TravelerLocator.getInstruction(traceData.tripStatus, travelerPosition);
        assertEquals(traceData.expectedInstruction, tripInstruction != null ? tripInstruction.build() : NO_INSTRUCTION, message);

        // If a Gwinnett County bus notification was sent, check that the agency, route, and trip id fields are not null.
        if (!travelerPosition.trackedJourney.busNotificationMessages.isEmpty() && currentLeg.route != null) {
            String firstMessageJson = travelerPosition.trackedJourney.busNotificationMessages.get(currentLeg.route.id);

            UsRideGwinnettBusOpNotificationMessage firstMessage = JsonUtils.getPOJOFromJSON(
                firstMessageJson, UsRideGwinnettBusOpNotificationMessage.class
            );

            assertNotNull(firstMessage.agency_id);
            assertNotNull(firstMessage.from_route_id);
            assertNotNull(firstMessage.from_trip_id);
            assertNotNull(firstMessage.to_route_id);
            assertNotNull(firstMessage.to_trip_id);
        }
    }

    private static Stream<Arguments> createBusStopTrace() {
        final int NORTH_WEST_BEARING = 315;

        Leg transitAsFirstLeg = firstLegBusTransit.legs.get(0);
        Coordinates busStopCoords = new Coordinates(transitAsFirstLeg.from);
        String busStopName = transitAsFirstLeg.from.name;

        return Stream.of(
            Arguments.of(
                "Deviated from the start of a trip which starts with a bus leg. Suggest path to head towards.",
                firstLegBusTransit,
                0,
                new TraceData()
                    .withTripStatus(TripStatus.DEVIATED)
                    .withPosition(createPoint(busStopCoords, 12, NORTH_WEST_BEARING))
                    .withExpectedInstruction(new DeviatedInstruction(busStopName, locale))
            ),
            Arguments.of(
                "On-time and near the initial bus stop on trip which starts with a bus leg. Instructs to wait for bus.",
                firstLegBusTransit,
                0,
                new TraceData()
                    .withPosition(createPoint(busStopCoords, 4, NORTH_WEST_BEARING))
                    .withExpectedInstruction(
                        new WaitForTransitInstruction(transitAsFirstLeg, transitAsFirstLeg.startTime.toInstant().minus(4, ChronoUnit.MINUTES), locale)
                    )
            ),
            Arguments.of(
                "On transit leg away from the boarding location (or walked past the bus stop). Instruct to continue riding.",
                firstLegBusTransit,
                0,
                new TraceData()
                    .withPosition(33.916779, -84.226556)
                    .withExpectedInstruction(new ContinueRidingTransitInstruction())
            ),
            Arguments.of(
                "Start live tracking well after bus departure. Issue wait instruction (indicate past departure).",
                firstLegBusTransit,
                0,
                new TraceData()
                    .withPosition(busStopCoords)
                    .withTripStatus(TripStatus.BEHIND_SCHEDULE)
                    .withInstant(Instant.now())
                    .withExpectedInstruction("Wait for your bus, route 20, scheduled at 7:58 AM (That time has passed)")
            ),
            Arguments.of(
                "Arrive at bus stop well after the bus departure (indicates past departure).",
                walkToBusTransition,
                0,
                new TraceData()
                    .withPosition(walkToBusTransition.legs.get(0).to.toCoordinates())
                    .withTripStatus(TripStatus.BEHIND_SCHEDULE)
                    .withInstant(Instant.now())
                    .withExpectedInstruction("Wait for your bus, route 40, scheduled at 6:41 AM (That time has passed)")
            ),
            Arguments.of(
                "Arrive at bus stop well in advance.",
                walkToBusTransition,
                0,
                new TraceData()
                    .withPosition(walkToBusTransition.legs.get(0).to.toCoordinates())
                    .withTripStatus(TripStatus.AHEAD_OF_SCHEDULE)
                    .withInstant(walkToBusTransition.legs.get(1).startTime.toInstant().minus(40, ChronoUnit.MINUTES))
                    .withExpectedInstruction("Wait 40 minutes for your bus, route 40, scheduled at 6:41 AM (On time)")
            ),
            Arguments.of(
                "After boarding bus and bus starts moving, but incorrectly produced 'COMPLETED' status.",
                walkToBus20,
                1,
                new TraceData()
                    .withPosition(WALK_AND_TRANSIT_LEG_OVERLAP_POINT)
                    .withSpeed(8)
                    .withTripStatus(TripStatus.ON_SCHEDULE)
                    .withInstant(Instant.ofEpochMilli(1740268915))
                    .withExpectedInstruction("Ride 4 min / 7 stops to Buford Hwy at Steve Dr (Accu-Car Expo)")
            )
        );
    }

    @ParameterizedTest
    @MethodSource("createTransitRideTrace")
    void canTrackTransitRide(String message, TraceData traceData) {
        Itinerary itinerary = midtownToAnsleyItinerary;
        Leg transitLeg = itinerary.legs.get(1);

        // In some cases, simulate missing intermediateStops field from OTP. Tests should still run to end.
        if (traceData.dismissIntermediateStops) {
            transitLeg.intermediateStops = null;
        }

        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(transitLeg)
            .setCurrentPosition(traceData.position)
            // Unless specified in traceData, an instant corresponding to around the trip start time should be provided
            // for correct instructions to be produced.
            .setCurrentTime(traceData.instant != null ? traceData.instant : transitLeg.startTime.toInstant())
            .setSpeed(traceData.speed)
            .build();
        travelerPosition.locale = locale;
        TripInstruction tripInstruction = TravelerLocator.getInstruction(traceData.tripStatus, travelerPosition);
        assertEquals(traceData.expectedInstruction, tripInstruction != null ? tripInstruction.build() : NO_INSTRUCTION, message);
    }

    private static Stream<Arguments> createTransitRideTrace() {
        final int SOUTH_WEST_BEARING = 225;
        Leg transitLeg = midtownToAnsleyItinerary.legs.get(1);
        String destinationName = transitLeg.to.name;

        Coordinates originCoords = new Coordinates(transitLeg.from);
        Coordinates destinationCoords = new Coordinates(transitLeg.to);

        return Stream.of(
            Arguments.of(
                "If present at the transit stop after the trip departure, there should not be an instruction.",
                new TraceData()
                    .withPosition(originCoords)
                    .withExpectedInstruction("Wait for your bus, route 27, scheduled at 9:18 AM (That time has passed)")
                    .withTripStatus(TripStatus.BEHIND_SCHEDULE)
                    .withInstant(Instant.now())
            ),
            Arguments.of(
                "At the bus stop, or just boarded the transit vehicle leg, it should still tell the user to board the bus.",
                new TraceData()
                    .withPosition(originCoords)
                    .withExpectedInstruction(
                        new WaitForTransitInstruction(transitLeg, transitLeg.startTime.toInstant(), locale)
                    )
            ),
            // This instruction can be missed if the transit vehicle is in a slow/congested area
            // with speeds less than 5 meters/second (11.1 mph, 18 km/h).
            Arguments.of(
                "Summarize the transit trip as vehicle departs.",
                new TraceData()
                    .withPosition(33.78647, -84.38041)
                    .withSpeed(6) // meters per second, ~13.4 mph or 21.6 km/h. The threshold is 5 meters per second.
                    .withExpectedInstruction(String.format("Ride 4 min / 8 stops to %s", destinationName))
            ),
            Arguments.of(
                "On the transit segment, but far from the arrival stop, an instruction to continue riding is given.",
                new TraceData()
                    .withPosition(33.78792, -84.37776)
                    .withExpectedInstruction("Continue riding the bus.")
            ),
            Arguments.of(
                "Upcoming arrival stop instruction.",
                new TraceData()
                    .withPosition(33.79139, -84.37441)
                    .withExpectedInstruction(String.format("Your stop is coming up (%s)", destinationName))
            ),
            Arguments.of(
                "Between the third and second to last stop.",
                new TraceData()
                    .withPosition(33.79362, -84.37235)
                    .withExpectedInstruction(String.format("Your stop is coming up (%s)", destinationName))
            ),
            Arguments.of(
                "One-stop warning (only within 'upcoming' distance of that stop) before the stop to get off",
                new TraceData()
                    .withPosition(33.79445, -84.37156)
                    .withExpectedInstruction(String.format("Get off at next stop (%s)", destinationName))
            ),
            Arguments.of(
                "Past the one-stop warning from the stop where you should get off.",
                new TraceData()
                    .withPosition(33.79478, -84.37127)
                    .withExpectedInstruction(String.format("Get off at next stop (%s)", destinationName))
                    .withNullIntermediateStops()
            ),
            Arguments.of(
                "Past the one-stop warning from the stop where you should get off (#2).",
                new TraceData()
                    .withPosition(33.79489, -84.37115)
                    .withExpectedInstruction(String.format("Get off at next stop (%s)", destinationName))
            ),
            Arguments.of(
                "Instruction approaching or at the stop where you should get off.",
                new TraceData()
                    .withPosition(createPoint(destinationCoords, 8, SOUTH_WEST_BEARING))
                    .withExpectedInstruction(String.format("Get off here (%s)", destinationName))
            ),
            Arguments.of(
                "No instruction provided besides trip status if bus is deviated or user missed their stop.",
                new TraceData()
                    .withPosition(33.79371, -84.37711)
                    .withTripStatus(TripStatus.DEVIATED)
                    .withExpectedInstruction(NO_INSTRUCTION)
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
        // then skip the check because the second to last waypoint will not be related to the last leg position.
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

    /**
     * Handles cases where the distance to end of leg was previously incorrectly computed.
     */
    @ParameterizedTest
    @MethodSource("createDistanceToStartOfLegCases")
    void testGetDistanceToStartOfLeg(Itinerary itinerary, Coordinates coordinates, boolean isWithinRadius) {
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.locations = List.of(
            new TrackingLocation(Instant.now(), coordinates.lat, coordinates.lon)
        );
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, itinerary, null);

        assertEquals(isWithinRadius, TravelerLocator.isAtStartOfLeg(travelerPosition));
    }

    private static Stream<Arguments> createDistanceToStartOfLegCases() {
        return Stream.of(
            // Close to start of routing (outside of origin building) for walk trip to One Justice Square
            Arguments.of(walkGjacTo1js, new Coordinates(33.951786, -83.992887), true),
            // Inside of origin building away from start of routing for walk trip to One Justice Square
            Arguments.of(walkGjacTo1js, new Coordinates(33.951563, -83.992954), false)
        );
    }

    /**
     * Handles cases where the distance to end of leg was previously incorrectly computed.
     */
    @ParameterizedTest
    @MethodSource("createDistanceToEndOfLegCases")
    void testGetDistanceToEndOfLeg(Itinerary itinerary, Coordinates coordinates, boolean isWithinRadius) {
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.locations = List.of(
            new TrackingLocation(Instant.now(), coordinates.lat, coordinates.lon)
        );
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, itinerary, null);

        List<Coordinates> legPositions = TravelerLocator.injectWaypointsIntoLegPositions(
            travelerPosition.expectedLeg,
            travelerPosition.expectedLeg.steps,
            GMAP_UPCOMING_RADIUS
        );

        assertEquals(isWithinRadius, TravelerLocator.getDistanceToEndOfLeg(travelerPosition, legPositions) <= GMAP_UPCOMING_RADIUS);
    }

    private static Stream<Arguments> createDistanceToEndOfLegCases() {
        return Stream.of(
            // At end of routing for walk trip to One Justice Square
            Arguments.of(walkGjacTo1js, new Coordinates(33.95242212998748, -83.99714406536067), true),
            // Near end of walk step/beginning of bus step for walk+bus trip
            Arguments.of(walkToBus20, WALK_AND_TRANSIT_LEG_OVERLAP_POINT, false)
        );
    }

    @ParameterizedTest
    @MethodSource("createDetectTransitLegCases")
    void canDetectTransitLeg(int speed, boolean expected) {
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.locations = List.of(
            new TrackingLocation(0, WALK_AND_TRANSIT_LEG_OVERLAP_POINT.lat, WALK_AND_TRANSIT_LEG_OVERLAP_POINT.lon, speed, Date.from(Instant.now()))
        );
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBus20, null);

        assertEquals(expected ? walkToBus20.legs.get(1) : walkToBus20.legs.get(0), travelerPosition.expectedLeg);
    }

    private static Stream<Arguments> createDetectTransitLegCases() {
        return Stream.of(
            Arguments.of(0, false),
            Arguments.of(4, false),
            Arguments.of(6, true)
        );
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
