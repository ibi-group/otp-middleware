package org.opentripplanner.middleware.triptracker;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TrackedJourney;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;

class NotifyBusOperatorTest {

    private static Itinerary walkToBusTransition;

    @BeforeAll
    public static void setUp() throws IOException {
        // Load default env.yml configuration.
        ConfigUtils.loadConfig(new String[] {});

        walkToBusTransition = JsonUtils.getPOJOFromJSON(
            CommonTestUtils.getTestResourceAsString("controllers/api/walk-to-bus-transition.json"),
            Itinerary.class
        );
    }

    @Test
    void canNotifyBusOperatorEndToEnd() {
        NotifyBusOperator.getBusOperatorNotifierQualifyingRoutes(List.of("GwinnettCountyTransit:40"));
        Leg walkLeg = walkToBusTransition.legs.get(0);
        Leg busLeg = walkToBusTransition.legs.get(1);
        Coordinates legToCoords = new Coordinates(walkLeg.to);
        TrackedJourney trackedJourney = new TrackedJourney();
        trackedJourney.locations.add(new TrackingLocation(legToCoords.lat, legToCoords.lon, Date.from(Instant.now())));
        OtpUser otpUser = new OtpUser();
        TravelerPosition travelerPosition = new TravelerPosition(trackedJourney, walkToBusTransition, otpUser);
        String tripInstruction = TravelerLocator.getInstruction(TripStatus.ON_SCHEDULE, travelerPosition, false);
        TripInstruction expectInstruction = new TripInstruction(busLeg, Instant.now());
        assertEquals(expectInstruction.build(), Objects.requireNonNullElse(tripInstruction, NO_INSTRUCTION), "");
    }


}