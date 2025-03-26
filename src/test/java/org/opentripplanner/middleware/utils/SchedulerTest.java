package org.opentripplanner.middleware.utils;

import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerTest {

    private static final long MILLIS_PER_DAY = 86_400_000L;

    @Test
    void canGetInitialDelayMillis() {
        long initialDelayMillis = Scheduler.getInitialDelayMillis("07:00");
        assertTrue(initialDelayMillis > 0L);
        assertTrue(initialDelayMillis < MILLIS_PER_DAY);
    }

    @Test
    void canHandleInvalidInitialDelayMillis() {
        long initialDelayMillis = 0L;
        try {
            initialDelayMillis = Scheduler.getInitialDelayMillis("*ThIs iS BoGuS*");
        } catch (DateTimeParseException e) {
            assertEquals(initialDelayMillis, 0L);
        }
    }
    
}
