package org.opentripplanner.middleware.triptracker.instruction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaitForTransitInstructionTest {
    @ParameterizedTest
    @MethodSource("getWaitTimeMinutesCases")
    void testGetWaitTimeInMinutes(int legOffsetSeconds, int expectedWaitMinutes, String message) {
        Instant now = Instant.now();
        Instant legStart = now.plusSeconds(legOffsetSeconds);
        Leg transitLeg = new Leg();
        transitLeg.startTime = Date.from(legStart);

        assertEquals(
            expectedWaitMinutes,
            new WaitForTransitInstruction(transitLeg, now, Locale.US).getWaitInMinutes(),
            message
        );
    }

    static Stream<Arguments> getWaitTimeMinutesCases() {
        // Note that 310 seconds are used to avoid millisecond shifts that could happen right at the 300s mark.
        return Stream.of(
            Arguments.of(0, 0, "zero wait"),
            Arguments.of(-310, -5, "5 min past"),
            Arguments.of(310, 5, "5 min wait")
        );
    }
}
