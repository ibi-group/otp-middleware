package org.opentripplanner.middleware;

import org.junit.jupiter.api.BeforeAll;
import org.opentripplanner.middleware.spark.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This abstract class contains is used to start a test instance of datatools-server that other tests can use to perform
 * various tests.
 *
 * A majority of the test involves setting up the proper config files for performing tests - especially the e2e tests.
 */
public abstract class OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(OtpMiddlewareTest.class);
    private static boolean setUpIsDone = false;

    @BeforeAll
    public static void setUp() throws RuntimeException, IOException {
        if (setUpIsDone) {
            return;
        }
        LOG.info("OtpMiddlewareTest setup");

        LOG.info("Starting server");
        Main.main(new String[]{"configurations/test/env.yml"});
        setUpIsDone = true;
    }
}
