package org.opentripplanner.middleware;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.opentripplanner.middleware.TestUtils.getBooleanEnvVar;

/**
 * This abstract class is used to start a test instance of otp-middleware that other tests can use to perform various
 * tests.
 */
public abstract class OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(OtpMiddlewareTest.class);
    private static boolean setUpIsDone = false;

    /**
     * Set up the otp-middleware application in order for tests to run properly. If test classes that implement this
     * class have additional setup logic that needs to be performed, they can add their own setup method which will be
     * run after this method is invoked. (Note: the BeforeAll annotation must be applied here in order for the set up
     * method to be invoked before all other tests.)
     */
    @BeforeAll
    public static void setUp() throws RuntimeException, IOException {
        if (setUpIsDone) {
            return;
        }
        LOG.info("OtpMiddlewareTest setup");

        LOG.info("Starting server");
        OtpMiddlewareMain.inTestEnvironment = true;
        // If in the e2e environment, use the secret env.yml file to start the server.
        // TODO: When ran on Travis CI, this file will automatically be setup.
        String[] args = getBooleanEnvVar("RUN_E2E")
            ? new String[]{"configurations/default/env.yml"}
            : new String[]{"configurations/test/env.yml"};
        // Fail this test and others if the above files do not exist.
        for (String arg : args) {
            File f = new File(arg);
            if (!f.exists() || f.isDirectory()) {
                throw new IOException(String.format("Required config file %s does not exist!", f.getName()));
            }
        }
        OtpMiddlewareMain.main(args);
        setUpIsDone = true;
    }
}
