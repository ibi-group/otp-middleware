package org.opentripplanner.middleware.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Responsible for constructing requests to an elected OTP server end point using original query parameters provided by
 * the requester. To provide the response from the OTP server in the form of status code and body so the correct
 * response can be provided to the requester.
 */
public class OtpDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcher.class);
    private static String OTP_SERVER = getConfigPropertyAsText("OTP_SERVER");
    private static final int OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS = 10;

    /**
     * Provides a response from the OTP server target service based on the query parameters provided.
     */
    public static OtpDispatcherResponse serviceRequest(String query, String endpoint) {
        LOG.debug("Original query string: {}", query);
        return call(buildUri(query, endpoint));
    }

    /**
     * Constructs a URL based on the otp server URL and the requester's target service (e.g. plan) and query
     * parameters.
     */
    private static URI buildUri(String params, String endPoint) {
        UriBuilder uriBuilder = UriBuilder.fromUri(OTP_SERVER)
            .path(endPoint)
            .replaceQuery(params);
        URI uri = URI.create(uriBuilder.toString());
        LOG.debug("Constructed URI: {}", uri);
        return uri;
    }

    /**
     * Makes a call to the OTP server end point. The original response and status are wrapped in a
     * single object and returned. It will fail if a connection is not made.
     */
    private static OtpDispatcherResponse call(URI uri) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS))
                .GET()
                .build();

        OtpDispatcherResponse otpDispatcherResponse = null;

        // get response from OTP
        try {
            HttpResponse<String> otpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            otpDispatcherResponse = new OtpDispatcherResponse();
            otpDispatcherResponse.responseBody = otpResponse.body();
            otpDispatcherResponse.statusCode = otpResponse.statusCode();
            LOG.debug("Response from OTP server: {}", otpDispatcherResponse.toString());
        } catch (InterruptedException | IOException e) {
            LOG.error("Error requesting OTP data", e);
        }

        return otpDispatcherResponse;
    }
}