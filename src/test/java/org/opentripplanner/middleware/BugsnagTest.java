package org.opentripplanner.middleware;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagReporter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BugsnagTest extends OtpMiddlewareTest {

    @Test
    public void notifyOfBug() {
        assertEquals(BugsnagReporter.getHook().notify(new RuntimeException("Unit test")), true);
    }

//    @Test
//    public void getData() {
//
//    }

}
