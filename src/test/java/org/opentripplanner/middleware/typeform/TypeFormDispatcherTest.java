package org.opentripplanner.middleware.typeform;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeFormDispatcherTest {

    @Test
    void canMakeResponsesParams() {
        LocalDateTime date = LocalDateTime.of(2024, 10, 25, 0, 0);

        String s = TypeFormDispatcher.responsesParams(date);

        // Using epoch timestamps to avoid conversions from local to UTC time which TypeForm requires.
        assertEquals("?page_size=1000&since=1729839600&until=1729925999", s);
    }
}

