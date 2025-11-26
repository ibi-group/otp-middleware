package org.opentripplanner.middleware.otp;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpLegResponseWrapper;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegFinderTest {
    @ParameterizedTest
    @MethodSource("legExistsCases")
    void existingLeg(Leg leg, boolean expected) {
        OtpLegResponseWrapper response = new OtpLegResponseWrapper();
        response.data.leg = leg;

        OtpDispatcherResponse otpDispatcherResponse = new OtpDispatcherResponse();
        otpDispatcherResponse.statusCode = HttpStatus.OK_200;
        otpDispatcherResponse.responseBody = JsonUtils.toJson(response);
        LegFinder legFinder = new LegFinder(ignored -> otpDispatcherResponse);
        assertEquals(expected, legFinder.queryLeg("leg-id") != null);
    }

    private static Stream<Arguments> legExistsCases() {
        Leg existingLeg = new Leg();
        existingLeg.id = "leg-id";
        existingLeg.transitLeg = true;
        existingLeg.startTime = DateTimeUtils.nowAsDate();
        existingLeg.endTime = existingLeg.startTime;
        existingLeg.departureDelay = 30;
        existingLeg.arrivalDelay = 60;

        return Stream.of(
            Arguments.of(existingLeg, true),
            Arguments.of(null, false)
        );
    }

}
