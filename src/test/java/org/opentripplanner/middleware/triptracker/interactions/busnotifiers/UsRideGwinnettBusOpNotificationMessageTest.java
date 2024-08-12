package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsRideGwinnettBusOpNotificationMessageTest {
    @ParameterizedTest
    @MethodSource("createGetMobilityCodeCases")
    void testGetMobilityCode(String mobilityMode, int expectedCode) {
        List<Integer> sentCodes = UsRideGwinnettBusOpNotificationMessage.getMobilityCode(mobilityMode);
        assertEquals(1, sentCodes.size());
        assertEquals(expectedCode, sentCodes.get(0));
    }

    private static Stream<Arguments> createGetMobilityCodeCases() {
        return Stream.of(
            Arguments.of("MScooter", 2),
            Arguments.of("Device-LowVision", 8),
            // This test is more about the edge cases than when a mode that denotes disability is passed.
            Arguments.of("None", 0),
            Arguments.of("", 0),
            Arguments.of("Unknown-mode", 0),
            Arguments.of(null, 0)
        );
    }
}
