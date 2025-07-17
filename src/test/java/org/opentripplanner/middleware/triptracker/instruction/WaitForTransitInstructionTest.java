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

    @ParameterizedTest
    @MethodSource("getStatusCases")
    void testGetStatus(int waitMinutes, boolean realTime, int delaySeconds, String expectedStatus, String message) {
        Instant now = Instant.now();
        Leg transitLeg = new Leg();
        transitLeg.startTime = Date.from(now);
        transitLeg.realTime = realTime;
        if (realTime) {
            transitLeg.departureDelay = delaySeconds;
        }

        assertEquals(
            expectedStatus,
            new WaitForTransitInstruction(transitLeg, now, Locale.US).getStatus(waitMinutes),
            message
        );
    }

    static Stream<Arguments> getStatusCases() {
        return Stream.of(
            Arguments.of(0, false, 0, " (No real-time info)", "zero wait"),
            Arguments.of(300, false, 0, " (No real-time info)", "5 min wait"),
            Arguments.of(-300, false, 0, " (That time has passed)", "5 min past"),
            Arguments.of(0, true, 0, ", on time", "zero delay"),
            Arguments.of(300, true, 0, ", on time", "5 min wait"),
            Arguments.of(300, true, 160, ", now 2 minutes late", "5 min wait incl 2 min delay"),
            Arguments.of(300, true, -160, ", now 2 minutes early", "5 min wait incl 2 min advance"),
            Arguments.of(-300, false, 100, " (That time has passed)", "5 min past incl 1 min delay")
        );
    }
}
