package org.opentripplanner.middleware.otp;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Responsible for constructing requests to an elected OTP server end point using original query parameters provided by
 * the requester. To provide the response from the OTP server in the form of status code and body so the correct
 * response can be provided to the requester.
 */
public class OtpDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcher.class);
    /**
     * URI location of the OpenTripPlanner API (e.g., https://otp-server.com/otp). Requests sent to this URI should
     * return OTP version info.
     */
    public static final String OTP_SERVER = getConfigPropertyAsText("OTP_SERVER");

    /**
     * Location of the plan endpoint (e.g., /plan).
     */
    public static final String OTP_PLAN_ENDPOINT = getConfigPropertyAsText("OTP_PLAN_ENDPOINT", "/routers/default/plan");

    private static String OTP_API_ROOT = getConfigPropertyAsText("OTP_API_ROOT");

    private static final int OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS = 10;

    /**
     * Provides a response from the OTP server target service based on the query parameters provided.
     */
    public static OtpDispatcherResponse sendOtpRequest(String query, String path) {
        LOG.debug("Original query string: {}", query);
        return sendOtpRequest(buildOtpUri(query, path));
    }

    /**
     * Provides a response from the OTP server target service based on the query parameters provided.
     */
    public static OtpDispatcherResponse sendOtpPlanRequest(String query) {
        LOG.debug("Original query string: {}", query);
        return sendOtpRequest(buildOtpUri(query, OTP_PLAN_ENDPOINT));
    }

    /**
     * Provides a response from the OTP server target service based on the query parameters provided.
     */
    public static OtpDispatcherResponse sendOtpPlanRequest(String from, String to) {
        return sendOtpPlanRequest(String.format("fromPlace=%s&toPlace=%s", from, to));
    }

    /**
     * Constructs a URL based on the otp server URL and the requester's target service (e.g. plan) and query
     * parameters.
     */
    private static URI buildOtpUri(String params, String path) {
        UriBuilder uriBuilder = UriBuilder.fromUri(OTP_API_ROOT)
            .path(path)
            .replaceQuery(params);
        URI uri = URI.create(uriBuilder.toString());
        LOG.debug("Constructed URI: {}", uri);
        return uri;
    }

    /**
     * Makes a call to the OTP server end point. The original response and status are wrapped in a
     * single object and returned. It will fail if a connection is not made.
     */
    private static OtpDispatcherResponse sendOtpRequest(URI uri) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS))
                .GET()
                .build();

        // Get response from OTP
        OtpDispatcherResponse otpDispatcherResponse = null;
        try {
            HttpResponse<String> otpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            otpDispatcherResponse = new OtpDispatcherResponse(otpResponse);
        } catch (InterruptedException | IOException e) {
            LOG.error("Error requesting OTP data", e);
        }
        return otpDispatcherResponse;
    }
}
