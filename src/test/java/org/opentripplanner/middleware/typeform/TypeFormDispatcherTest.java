package org.opentripplanner.middleware.typeform;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeFormDispatcherTest extends OtpMiddlewareTestEnvironment {
    @Test
    void canMakeResponsesParams() {
        // October 25, 2024 00:00, US Pacific Daylight Time
        // CI sets OTP-middleware in that timezone.
        // The timestamps in the expected string below correspond to 00:00 and 23:59, also in that timezone.
        LocalDateTime date = LocalDateTime.of(2024, 10, 25, 0, 0);

        String s = TypeFormDispatcher.responsesParams(date);

        // Using epoch timestamps to avoid conversions from local to UTC time which TypeForm requires.
        assertEquals("?page_size=1000&since=1729839600&until=1729925999", s);
    }
}
