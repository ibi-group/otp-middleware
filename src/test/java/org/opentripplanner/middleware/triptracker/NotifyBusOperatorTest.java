package org.opentripplanner.middleware.triptracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MobilityProfile;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.AgencyAction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.BusOperatorActions;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettBusOpNotificationMessage;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettNotifyBusOperator;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettNotifyBusOperator.ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES;
import static org.opentripplanner.middleware.triptracker.interactions.busnotifiers.UsRideGwinnettNotifyBusOperator.getNotificationMessage;

class NotifyBusOperatorTest extends OtpMiddlewareTestEnvironment {

    private static Itinerary walkToBusTransition;

    private static TrackedJourney trackedJourney;

    private static final String routeId = "GwinnettCountyTransit:40";

    private static final Locale locale = Locale.US;

    private final BusOperatorActions busOperatorActions = new BusOperatorActions(List.of(
        new AgencyAction("GCT", UsRideGwinnettNotifyBusOperator.class.getName())
    ));

    @BeforeAll
    public static void setUp() throws IOException {
        // This itinerary is from OTP2 and has been modified to work with OTP1 to avoid breaking changes.
        walkToBusTransition = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-transition.json"),
            Itinerary.class
        );
        UsRideGwinnettNotifyBusOperator.IS_TEST = true;
        UsRideGwinnettNotifyBusOperator.US_RIDE_GWINNETT_QUALIFYING_BUS_NOTIFIER_ROUTES = List.of(routeId);
    }

    @AfterEach
    public void tearDown() {
        if (trackedJourney != null) {
            trackedJourney.delete();
        }
    }

    @Test
    void canNotifyBusOperatorForScheduledDeparture() {
        trackedJourney = createAndPersistTrackedJourney(getEndOfWalkLegCoordinates());
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());
        String tripInstruction = TravelerLocator.getInstruction(TripStatus.ON_SCHEDULE, travelerPosition, false);
        Leg busLeg = walkToBusTransition.legs.get(1);
        TripInstruction expectInstruction = new TripInstruction(busLeg, Instant.now(), locale);
        TrackedJourney updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertTrue(updated.busNotificationMessages.containsKey(routeId));
        assertEquals(expectInstruction.build(), tripInstruction);
    }

    @Test
    void canNotifyBusOperatorForDelayedDeparture() throws CloneNotSupportedException {
        // Copy itinerary so changes can be made to it without impacting other tests.
        Itinerary itinerary = walkToBusTransition.clone();
        itinerary.legs.get(1).departureDelay = 10;

        Leg walkLeg = itinerary.legs.get(0);
        Instant timeAtEndOfWalkLeg = walkLeg.endTime.toInstant();
        timeAtEndOfWalkLeg = timeAtEndOfWalkLeg.minusSeconds(120);

        trackedJourney = createAndPersistTrackedJourney(true, getEndOfWalkLegCoordinates(), timeAtEndOfWalkLeg);
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, itinerary, createOtpUser());
        String tripInstruction = TravelerLocator.getInstruction(TripStatus.ON_SCHEDULE, travelerPosition, false);

        Leg busLeg = itinerary.legs.get(1);
        TripInstruction expectInstruction = new TripInstruction(busLeg, timeAtEndOfWalkLeg, locale);
        assertEquals(expectInstruction.build(), tripInstruction);
    }

    @Test
    void canCancelBusOperatorNotification() throws JsonProcessingException {
        trackedJourney = createAndPersistTrackedJourney(getEndOfWalkLegCoordinates());
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());
        busOperatorActions.handleSendNotificationAction(TripStatus.ON_SCHEDULE, travelerPosition);
        TrackedJourney updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertTrue(updated.busNotificationMessages.containsKey(routeId));
        busOperatorActions.handleCancelNotificationAction(travelerPosition);
        updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        String messageBody = updated.busNotificationMessages.get(routeId);
        UsRideGwinnettBusOpNotificationMessage message = getNotificationMessage(messageBody);
        assertTrue(message.msg_type == 1);
    }

    @Test
    void canNotifyBusOperatorOnlyOnce() {
        trackedJourney = createAndPersistTrackedJourney(getEndOfWalkLegCoordinates());
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());
        busOperatorActions.handleSendNotificationAction(TripStatus.ON_SCHEDULE, travelerPosition);
        TrackedJourney updated = Persistence.trackedJourneys.getById(trackedJourney.id);
        assertTrue(updated.busNotificationMessages.containsKey(routeId));
        assertFalse(UsRideGwinnettNotifyBusOperator.hasNotSentNotificationForRoute(trackedJourney, routeId));
    }

    @ParameterizedTest
    @MethodSource("createWithinOperationalNotifyWindowTrace")
    void isWithinOperationalNotifyWindow(
        boolean expected,
        TripStatus tripStatus,
        TravelerPosition travelerPosition,
        String message
    ) {
        assertEquals(expected, UsRideGwinnettNotifyBusOperator.isWithinOperationalNotifyWindow(tripStatus, travelerPosition), message);
    }

    private static Stream<Arguments> createWithinOperationalNotifyWindowTrace() {
        Leg walkLeg = walkToBusTransition.legs.get(0);
        Instant timeAtEndOfWalkLeg = walkLeg.endTime.toInstant();
        TrackedJourney trackedJourney = createAndPersistTrackedJourney(
            false,
            getEndOfWalkLegCoordinates(),
            timeAtEndOfWalkLeg
        );
        TravelerPosition travelerPosition1 = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());
        trackedJourney = createAndPersistTrackedJourney(
            false,
            getEndOfWalkLegCoordinates(),
            timeAtEndOfWalkLeg.plusSeconds(60 * ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES)
        );
        TravelerPosition travelerPosition2 = new TravelerPosition(trackedJourney, walkToBusTransition, createOtpUser());
        return Stream.of(
            Arguments.of(true, TripStatus.ON_SCHEDULE, null, "Traveler is on schedule, notification can be sent."),
            Arguments.of(false, TripStatus.BEHIND_SCHEDULE, null, "Traveler is behind schedule, notification can not be sent."),
            Arguments.of(true, TripStatus.AHEAD_OF_SCHEDULE, travelerPosition1, "Traveler is ahead of schedule, but within the notify window."),
            Arguments.of(false, TripStatus.AHEAD_OF_SCHEDULE, travelerPosition2, "Too far ahead of schedule to notify bus operator.")
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
        return createAndPersistTrackedJourney(true, legToCoords, Instant.now());
    }

    private static TrackedJourney createAndPersistTrackedJourney(boolean persist, Coordinates legToCoords, Instant dateTime) {
        trackedJourney = new TrackedJourney();
        trackedJourney.locations.add(new TrackingLocation(legToCoords.lat, legToCoords.lon, Date.from(dateTime)));
        if (persist) Persistence.trackedJourneys.create(trackedJourney);
        return trackedJourney;
    }

    private static Coordinates getEndOfWalkLegCoordinates() {
        Leg walkLeg = walkToBusTransition.legs.get(0);
        return new Coordinates(walkLeg.to);
    }
}