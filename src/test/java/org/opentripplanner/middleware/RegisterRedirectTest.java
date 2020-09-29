package org.opentripplanner.middleware;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegisterRedirectTest {

    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        OtpMiddlewareTest.setUp();
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
            HttpUtils.REQUEST_METHOD.GET,
            null,
            ""
        );

        assertEquals(HttpStatus.FOUND_302, response.statusCode());
        HttpHeaders httpHeaders = response.headers();
        assertEquals(redirect, httpHeaders.firstValue("location").get());
    }
}