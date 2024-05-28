package org.opentripplanner.middleware.triptracker.interactions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MobilityProfile;
import org.opentripplanner.middleware.models.OtpUser;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsGdotGwinnettTrafficSignalNotifierTest {

    private static final String EXTENDED_PHASE_MESSAGE =
        "Extended phase should always be requested, except for the 'None' and null profiles";
    public static final String DUMMY_KEY = "secret";

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

    private static UsGdotGwinnettTrafficSignalNotifier createNotifier() {
        return new UsGdotGwinnettTrafficSignalNotifier(
            "http://pedsignal.example.com",
            "/signal-path/%s/crossing-path/%s/trigger-path",
            DUMMY_KEY
        );
    }

    @Test
    void testGetUrl() {
        String signalId = "signal-12";
        String crossingId = "crossing-114";
        UsGdotGwinnettTrafficSignalNotifier notifier = createNotifier();
        assertEquals("http://pedsignal.example.com/signal-path/signal-12/crossing-path/crossing-114/trigger-path",  notifier.getUrl(signalId, crossingId, false));
        assertEquals("http://pedsignal.example.com/signal-path/signal-12/crossing-path/crossing-114/trigger-path?extended=true",  notifier.getUrl(signalId, crossingId, true));
    }

    @Test
    void testGetHeaders() {
        Map<String, String> headers = createNotifier().getHeaders();
        assertEquals(1, headers.size());
        assertTrue(headers.containsKey("X-API-KEY"));
        assertEquals(DUMMY_KEY, headers.get("X-API-KEY"));
    }
}
