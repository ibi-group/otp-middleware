package org.opentripplanner.middleware;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Tests for creating and deleting api keys. The following config parameters are required for these tests to run:
 *
 * DISABLE_AUTH set to true to bypass auth checks.
 * DEFAULT_USAGE_PLAN_ID set to a valid usage plan id. AWS requires this to create an api key.
 * OTP_MIDDLEWARE_URI_ROOT, e.g. http://localhost:4567.
 */
public class ApiKeyManagementTest extends OtpMiddlewareTest {

    private static final String OTP_MIDDLEWARE_URI_ROOT
        = getConfigPropertyAsText("OTP_MIDDLEWARE_URI_ROOT", "http://localhost:4567");

    private static final int CONNECTION_TIMEOUT_IN_SECONDS = 5;

    @Test @Disabled
    public void createApiKeyForUser() {
        // FIXME: User is derived from token so difficult to test.
/*
        URI uri = HttpUtils.buildUri(OTP_MIDDLEWARE_URI_ROOT, "/api/secure/application/apikey", null);
        HttpResponse<String> response = HttpUtils.httpRequestRawResponse(uri,
            CONNECTION_TIMEOUT_IN_SECONDS,
            HttpUtils.REQUEST_METHOD.POST,
            null,
            "");
        assertEquals(response.statusCode(), 200);

 */
    }

    @Test @Disabled
    public void getApiKeyForUser() {
        // TODO Complete once above has been agreed
    }

    @Test @Disabled
    public void deleteApiKeyForUser() {
        // TODO Complete once above has been agreed
    }
}
