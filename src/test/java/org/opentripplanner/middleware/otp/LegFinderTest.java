package org.opentripplanner.middleware.otp;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.itinerarymatching.ItineraryMatchingUtils;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpLegResponseWrapper;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegFinderTest {
    @ParameterizedTest
    @MethodSource("legExistsCases")
    void existingLeg(String requestedId, Leg returnedLeg, boolean expected) {
        OtpLegResponseWrapper response = new OtpLegResponseWrapper();
        response.data.leg = returnedLeg;

        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse();
        otpDispatcherResponse.statusCode = HttpStatus.OK_200;
        otpDispatcherResponse.responseBody = JsonUtils.toJson(response);
        LegFinder legFinder = new LegFinder(ignored -> otpDispatcherResponse, (l, ignored) -> l.id);
        assertEquals(expected, legFinder.queryLeg(requestedId) != null);
    }

    private static Stream<Arguments> legExistsCases() {
        LocalDateTime now = LocalDateTime.now();
        Leg existingLeg = ItineraryMatchingUtils.createBusLeg("leg-id", now, now);

        return Stream.of(
            Arguments.of("leg-id", existingLeg, true),
            Arguments.of("unexisting-leg-id", null, false)
        );
    }

}
