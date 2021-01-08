package org.opentripplanner.middleware;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.testutils.CommonTestUtils.IS_END_TO_END;
import static org.opentripplanner.middleware.utils.ConfigUtils.isRunningCi;

/**
 * This class is used to test generic endpoints of the otp-middleware server and also to start a test instance of
 * otp-middleware that other tests can use to perform various tests.
 */
public class OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(OtpMiddlewareTest.class);
    private static boolean setUpIsDone = false;

    /**
     * Set up the otp-middleware application in order for tests to run properly. If test classes that implement this
     * class have additional setup logic that needs to be performed, they can add their own setup method which will be
     * run after this method is invoked. (Note: the BeforeAll annotation must be applied here in order for the set up
     * method to be invoked before all other tests.)
     */
    @BeforeAll
    public static void setUp() throws RuntimeException, IOException, InterruptedException {
        if (setUpIsDone) {
            return;
        }
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
                throw new IOException(String.format("Required config file %s does not exist!", f.getName()));
            }
        }
        OtpMiddlewareMain.main(args);
        setUpIsDone = true;
    }

    /**
     * Test to confirm the correct redirect to required registration page.
     */
    @Test
    public void canRegisterRedirect() {
        String redirect = "http://localhost:3000/#/register";

        String path = String.format("register?route=%s", URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        HttpResponse<String> response = HttpUtils.httpRequestRawResponse(
            URI.create("http://localhost:4567/" + path),
            1000,
            HttpMethod.GET,
            null,
            ""
        );

        assertEquals(HttpStatus.FOUND_302, response.statusCode());
        HttpHeaders httpHeaders = response.headers();
        assertEquals(redirect, httpHeaders.firstValue("location").get());
    }
}
