package org.opentripplanner.middleware.otp;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegFinderTest {
    @ParameterizedTest
    @MethodSource("legExistsCases")
    void existingLeg(String responseBody, boolean expected) {
        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse();
        otpDispatcherResponse.statusCode = HttpStatus.OK_200;
        otpDispatcherResponse.responseBody = responseBody;
        LegFinder legFinder = new LegFinder(ignored -> otpDispatcherResponse);
        assertEquals(expected, legFinder.legExists("leg-id"));
    }

    private static Stream<Arguments> legExistsCases() {
        return Stream.of(
            Arguments.of("{ \"data\": { \"leg\": { \"id\": \"leg-id\" } } }", true),
            Arguments.of("{ \"data\": { \"leg\": null } }", false)
        );
    }

}
