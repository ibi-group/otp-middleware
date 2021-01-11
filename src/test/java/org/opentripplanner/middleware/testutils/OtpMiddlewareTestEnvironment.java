package org.opentripplanner.middleware.testutils;

import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.opentripplanner.middleware.utils.ConfigUtils.isRunningCi;

/**
 * This class contains various fields pertaining to the test environment and also contains a static block of code that
 * starts an OtpMiddleware server (which also loads the config and initializes the database). Every test class that
 * requires a test instance of the otp-middleware server to be running must extend this class. If test classes that
 * extend this class have additional setup logic that needs to be performed, they can add their own setup method with
 * the @BeforeAll decorator which will be run after the static code in the class is invoked.
 */
public class OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(OtpMiddlewareTestEnvironment.class);

    /**
     * Whether the end-to-end environment variable is enabled.
     */
    public static final boolean IS_END_TO_END = CommonTestUtils.getBooleanEnvVar("RUN_E2E");

    static final String TEST_RESOURCE_PATH = "src/test/resources/org/opentripplanner/middleware/";

    static {
        LOG.info("OtpMiddlewareTest setup");

        LOG.info("Starting server");
        OtpMiddlewareMain.inTestEnvironment = true;
        // If in the e2e environment, use the secret env.yml file to start the server.
        // TODO: When ran in a CI environment, this file will automatically be setup.
        String[] args;
        if (IS_END_TO_END) {
            LOG.info("running E2E tests");
            // Check if running in a CI environment. If so, use environment variables instead of config file.
            args = isRunningCi ? new String[]{} : new String[]{"configurations/default/env.yml"};
        } else {
            LOG.info("running unit tests");
            // If not running E2E, use test env.yml.
            args = new String[]{"configurations/test/env.yml"};
        }
        // Fail this test and others if the above files do not exist.
        for (String arg : args) {
            File f = new File(arg);
            if (!f.exists() || f.isDirectory()) {
                throw new RuntimeException(String.format("Required config file %s does not exist!", f.getName()));
            }
        }

        // start server
        try {
            OtpMiddlewareMain.main(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
