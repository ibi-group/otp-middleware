package org.opentripplanner.middleware.triptracker.interactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MobilityProfile;
import org.opentripplanner.middleware.models.OtpUser;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UsGdotGwinnettTrafficSignalNotifierTest {

    private static final String EXTENDED_PHASE_MESSAGE =
        "Extended phase should always be requested, except for the 'None' and null profiles";

    @Test
    void testNeedsExtendedPhaseNullProfileObj() {
        OtpUser otpUser = new OtpUser();
        otpUser.mobilityProfile = null;
        assertFalse(UsGdotGwinnettTrafficSignalNotifier.needsExtendedPhase(otpUser), EXTENDED_PHASE_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("createNeedsExtendedPhaseCases")
    void testNeedsExtendedPhase(String mobilityMode, boolean result) {
        MobilityProfile mobilityProfile = new MobilityProfile();
        mobilityProfile.mobilityMode = mobilityMode;
        OtpUser otpUser = new OtpUser();
        otpUser.mobilityProfile = mobilityProfile;

        assertEquals(result, UsGdotGwinnettTrafficSignalNotifier.needsExtendedPhase(otpUser), EXTENDED_PHASE_MESSAGE);
    }

    static Stream<Arguments> createNeedsExtendedPhaseCases() {
        return Stream.of(
            null,
            "None",
            "Some",
            "Device",
            "WChairM",
            "WChairE",
            "MScooter",
            "LowVision",
            "Blind",
            "Some-LowVision",
            "Device-LowVision",
            "WChairM-LowVision",
            "WChairE-LowVision",
            "MScooter-LowVision",
            "Some-Blind",
            "Device-Blind",
            "WChairM-Blind",
            "WChairE-Blind",
            "MScooter-Blind"
        ).map(
            m -> Arguments.of(m, m != null && !m.equals("None"))
        );
    }
}
