package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MobilityProfile;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.instruction.WaitForTransitInstruction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.AgencyAction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.BusOperatorActions;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettBusOpNotificationMessage;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettNotifyBusOperator;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES;
import static org.opentripplanner.middleware.triptracker.TravelerLocator.getBusDepartureTime;
import static org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettNotifyBusOperator.getNotificationMessage;

class NotifyBusOperatorTest extends OtpMiddlewareTestEnvironment {

    private static Itinerary walkToBusTransition;

    private static Itinerary firstLegBusTransit;

    private static TrackedJourney trackedJourney;

    private static final String ROUTE_ID = "GwinnettCountyTransit:40";

    private static final Locale locale = Locale.US;

    private final BusOperatorActions busOperatorActions = new BusOperatorActions(List.of(
        new AgencyAction("GCT", UsRideGwinnettNotifyBusOperator.class.getName())
    ));

    @BeforeAll
    public static void setUp() throws IOException {
        // These itineraries are from OTP2 and have been modified to work with OTP1 to avoid breaking changes.
        walkToBusTransition = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-transition.json"),
            Itinerary.class
        );
        firstLegBusTransit = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/first-leg-transit.json"),
            Itinerary.class
        );
        UsRideGwinnettNotifyBusOperator.IS_TEST = true;
        UsRideGwinnettNotifyBusOperator.US_RIDE_GWINNETT_QUALIFYING_BUS_NOTIFIER_ROUTES = List.of(ROUTE_ID);
    }

    @AfterEach
    public void tearDown() {
        if (trackedJourney != null) {
            trackedJourney.delete();
        }
    }

    @ParameterizedTest
    @MethodSource("creatNotifyBusOperatorForScheduledDepartureTrace")
    void canNotifyBusOperatorForScheduledDeparture(Leg busLeg, Itinerary itinerary, boolean isStartOfTrip, String message) {
        Coordinates startOfTransitCoordinates = new Coordinates(busLeg.from);
        Instant busDepartureTime = getBusDepartureTime(busLeg);
        trackedJourney = createAndPersistTrackedJourney(startOfTransitCoordinates, busDepartureTime);
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, itinerary, createOtpUser());
        TripInstruction tripInstruction = TravelerLocator.getInstruction(TripStatus.ON_SCHEDULE, travelerPosition, isStartOfTrip);
        TripInstruction expectInstruction = new WaitForTransitInstruction(busLeg, busDepartureTime, locale);
        TrackedJourney updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertTrue(updated.busNotificationMessages.containsKey(ROUTE_ID));
        assertEquals(expectInstruction.build(), tripInstruction.build(), message);
    }

    private static Stream<Arguments> creatNotifyBusOperatorForScheduledDepartureTrace() {
        return Stream.of(
            Arguments.of(
                firstLegBusTransit.legs.get(0),
                firstLegBusTransit,
                true,
                "Can notify bus operator when the first leg is transit."
            ),
            Arguments.of(
                walkToBusTransition.legs.get(1),
                walkToBusTransition,
                false,
                "Can notify bus operator when the next leg is transit."
            )
        );
    }

    @ParameterizedTest
    @MethodSource("shouldCancelBusNotificationForStartOfTripTrace")
    void shouldCancelBusNotificationForStartOfTrip(boolean expected, Leg expectedLeg, Coordinates currentPosition, String message) {
        Leg first = firstLegBusTransit.legs.get(0);
        TrackedJourney journey = new TrackedJourney();
        journey.busNotificationMessages.put(ROUTE_ID, "{\"msg_type\": 1}");
        TravelerPosition travelerPosition = new TravelerPosition.Builder()
            .setExpectedLeg(expectedLeg)
            .setTrackedJourney(journey)
            .setFirstLegOfTrip(first)
            .setCurrentPosition(currentPosition).build();
        assertEquals(expected, ManageTripTracking.shouldCancelBusNotificationForStartOfTrip(travelerPosition), message);
    }

    private static Stream<Arguments> shouldCancelBusNotificationForStartOfTripTrace() {
        Leg first = firstLegBusTransit.legs.get(0);
        Coordinates atStartOfBusJourney = new Coordinates(first.from);
        Coordinates atEndOfBusJourney = new Coordinates(first.to);
        return Stream.of(
            Arguments.of(
                true,
                firstLegBusTransit.legs.get(0),
                atStartOfBusJourney,
                "Still waiting for bus, should cancel notification."
            ),
            Arguments.of(
                false,
                firstLegBusTransit.legs.get(1),
                atEndOfBusJourney,
                "Already on the bus, no need to cancel notification."
            )
        );
    }

    @Test
    void canNotifyBusOperatorForDelayedDeparture() throws CloneNotSupportedException {
        // Copy itinerary so changes can be made to it without impacting other tests.
        Itinerary itinerary = walkToBusTransition.clone();
        itinerary.legs.get(1).departureDelay = 10;

        Leg walkLeg = itinerary.legs.get(0);
        Instant timeAtEndOfWalkLeg = walkLeg.endTime.toInstant();
        timeAtEndOfWalkLeg = timeAtEndOfWalkLeg.minusSeconds(120);

        trackedJourney = createAndPersistTrackedJourney(getEndOfWalkLegCoordinates(), timeAtEndOfWalkLeg);
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, itinerary, createOtpUser());
        TripInstruction tripInstruction = TravelerLocator.getInstruction(TripStatus.ON_SCHEDULE, travelerPosition, false);
        assertNotNull(tripInstruction);

        Leg busLeg = itinerary.legs.get(1);
        TripInstruction expectInstruction = new WaitForTransitInstruction(busLeg, timeAtEndOfWalkLeg, locale);
        assertEquals(expectInstruction.build(), tripInstruction.build());
    }

    @Test
    void canCancelBusOperatorNotification() throws JsonProcessingException, InterruptedException {
        trackedJourney = createAndPersistTrackedJourney(getEndOfWalkLegCoordinates());
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());

        TrackedJourney updated = sendAndCheckInitialBusOperatorNotification(travelerPosition);
        assertEquals(1, getMessage(updated).msg_type);

        busOperatorActions.handleCancelNotificationAction(travelerPosition, travelerPosition.nextLeg);
        UsRideGwinnettBusOpNotificationMessage cancelMessage1 = getMessage(
            Persistence.trackedJourneys.getById(trackedJourney.id)
        );
        assertEquals(0, cancelMessage1.msg_type);


        // A second request to cancel should not touch the previous request.
        Thread.sleep(20);
        busOperatorActions.handleCancelNotificationAction(travelerPosition, travelerPosition.nextLeg);
        UsRideGwinnettBusOpNotificationMessage cancelMessage2 = getMessage(
            Persistence.trackedJourneys.getById(trackedJourney.id)
        );
        assertEquals(0, cancelMessage2.msg_type);
        assertEquals(cancelMessage1.timestamp, cancelMessage2.timestamp);
    }

    private static UsRideGwinnettBusOpNotificationMessage getMessage(TrackedJourney updated) throws JsonProcessingException {
        String messageBody = updated.busNotificationMessages.get(ROUTE_ID);
        return getNotificationMessage(messageBody);
    }

    @Test
    void canNotifyBusOperatorOnlyOnce() throws InterruptedException, JsonProcessingException {
        trackedJourney = createAndPersistTrackedJourney(getEndOfWalkLegCoordinates());
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());

        TrackedJourney updated = sendAndCheckInitialBusOperatorNotification(travelerPosition);

        // A second request to notify the operator should not touch the previous request.
        Thread.sleep(20);
        busOperatorActions.handleSendNotificationAction(travelerPosition, travelerPosition.nextLeg);
        TrackedJourney updated2 = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertFalse(UsRideGwinnettNotifyBusOperator.hasNotSentNotificationForRoute(updated2, ROUTE_ID));
        assertEquals(getMessage(updated).timestamp, getMessage(updated2).timestamp);
    }

    private TrackedJourney sendAndCheckInitialBusOperatorNotification(TravelerPosition travelerPosition) throws JsonProcessingException {
        Coordinates position = travelerPosition.currentPosition;
        MonitoredTrip trip = new MonitoredTrip();
        // Add a confirmed companion to this trip
        trip.companion = new RelatedUser();
        trip.companion.status = RelatedUser.RelatedUserStatus.CONFIRMED;

        // Endpoints indirectly call the TripTrackingData constructor that sets the trip field in Trackedjourney,
        // so we add that here to replicate those steps.
        TripTrackingData tripData = new TripTrackingData(
            trip,
            trackedJourney,
            List.of(new TrackingLocation(travelerPosition.currentTime, position.lat, position.lon))
        );
        assertNotNull(tripData.journey.trip);
        assertNotNull(trackedJourney.trip);

        busOperatorActions.handleSendNotificationAction(travelerPosition, travelerPosition.nextLeg);

        TrackedJourney updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertTrue(updated.busNotificationMessages.containsKey(ROUTE_ID));
        assertFalse(UsRideGwinnettNotifyBusOperator.hasNotSentNotificationForRoute(updated, ROUTE_ID));
        assertTrue(getMessage(updated).trusted_companion);

        return updated;
    }

    @ParameterizedTest
    @MethodSource("createWithinOperationalNotifyWindowTrace")
    void isWithinOperationalNotifyWindow(boolean expected, TravelerPosition travelerPosition, String message) {
        assertEquals(
            expected,
            TravelerLocator.isWithinOperationalNotifyWindow(travelerPosition.currentTime, travelerPosition.nextLeg),
            message
        );
    }

    private static Stream<Arguments> createWithinOperationalNotifyWindowTrace() {
        var busLeg = walkToBusTransition.legs.get(1);
        var busDepartureTime = getBusDepartureTime(busLeg);

        return Stream.of(
            Arguments.of(
                true,
                new TravelerPosition.Builder()
                    .setNextLeg(busLeg)
                    .setCurrentTime(busDepartureTime)
                    .build(),
                "Traveler is on schedule, notification can be sent."
            ),
            Arguments.of(
                false,
                new TravelerPosition.Builder()
                    .setNextLeg(busLeg)
                    .setCurrentTime(busDepartureTime.plusSeconds(60))
                    .build(),
                "Traveler is behind schedule, notification can not be sent."
            ),
            Arguments.of(
                true,
                new TravelerPosition.Builder()
                    .setNextLeg(busLeg)
                    .setCurrentTime(busDepartureTime.minusSeconds(60))
                    .build(),
                "Traveler is ahead of schedule, but within the notify window."
            ),
            Arguments.of(false,
                new TravelerPosition.Builder()
                    .setNextLeg(busLeg)
                    .setCurrentTime(busDepartureTime.plusSeconds((ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES + 1) * 60))
                    .build(),
                "Too far ahead of schedule to notify bus operator.")
        );
    }

    @ParameterizedTest
    @MethodSource("shouldSendBusNotificationAtStartOfTripTrace")
    void shouldSendBusNotificationAtStartOfTrip(boolean expected, TravelerPosition travelerPosition, String message) {
        assertEquals(expected, TravelerLocator.shouldSendBusNotification(travelerPosition.nextLeg, travelerPosition.currentTime), message);
    }

    private static Stream<Arguments> shouldSendBusNotificationAtStartOfTripTrace() {
        var busLeg = firstLegBusTransit.legs.get(0);
        var walkLeg = walkToBusTransition.legs.get(0);

        return Stream.of(
            Arguments.of(
                true,
                new TravelerPosition.Builder()
                    .setNextLeg(busLeg)
                    .setCurrentTime(getBusDepartureTime(busLeg))
                    .build(),
                "Traveler at the start of a trip which starts with a bus leg, should notify."
            ),
            Arguments.of(
                false,
                new TravelerPosition.Builder()
                    .setNextLeg(walkLeg)
                    .setCurrentTime(getBusDepartureTime(walkLeg))
                    .build(),
                "Traveler at the start of a trip which starts with a walk leg, should not notify."
            )
        );
    }

    private static OtpUser createOtpUser() {
        MobilityProfile mobilityProfile = new MobilityProfile();
        mobilityProfile.mobilityMode = "WChairE";
        OtpUser otpUser = new OtpUser();
        otpUser.mobilityProfile = mobilityProfile;
        return otpUser;
    }

    private static TrackedJourney createAndPersistTrackedJourney(Coordinates legToCoords) {
        return createAndPersistTrackedJourney(legToCoords, Instant.now());
    }

    private static TrackedJourney createAndPersistTrackedJourney(Coordinates legToCoords, Instant dateTime) {
        trackedJourney = new TrackedJourney();
        trackedJourney.locations.add(new TrackingLocation(legToCoords.lat, legToCoords.lon, Date.from(dateTime)));
        Persistence.trackedJourneys.create(trackedJourney);
        return trackedJourney;
    }

    private static Coordinates getEndOfWalkLegCoordinates() {
        Leg walkLeg = walkToBusTransition.legs.get(0);
        return new Coordinates(walkLeg.to);
    }
}