package org.opentripplanner.middleware;

import com.bugsnag.Bugsnag;
import org.junit.jupiter.api.Test;

public class BugsnagTest {

    String bugsnagApiKey = "423a4670558559fd47a23959f91054d3";

    @Test
    public void notifyOfBug() {
        Bugsnag bugsnag = new Bugsnag(bugsnagApiKey);
        bugsnag.notify(new RuntimeException("Hello world"));
    }

}
