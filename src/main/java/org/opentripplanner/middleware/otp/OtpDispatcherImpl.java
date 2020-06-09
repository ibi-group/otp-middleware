package org.opentripplanner.middleware.otp;

import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Responsible for constructing requests to elected OTP server end point using original query parameters provided by MOD UI.
 * To provide the response from the OTP server in the form of status code and body so the correct response can be provided to the calling MOD UI.
 */
public class OtpDispatcherImpl implements OtpDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcherImpl.class);
    private String OTP_SERVER;
    private final int OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS = 10;

    /**
     * Constructs class with required OTP server url
     */
    public OtpDispatcherImpl(String otpServer) {
        LOG.debug("OTP Server: {}", otpServer);
        this.OTP_SERVER = otpServer;
    }

    /**
     * Provides a plan response from the OTP server based on the query parameters provided
     */
    @Override
    public OtpDispatcherResponse getPlan(String query, String endPoint) {
        LOG.debug("Original query string: {}", query);
        return call(buildUri(query, endPoint));
    }

    /**
     * Constructs a url based on the otp server url, the (plan) endpoint and query params provided by MOD UI
     */
    private URI buildUri(String params, String endPoint) {
        UriBuilder uriBuilder = UriBuilder.fromUri(OTP_SERVER)
            .path(endPoint)
            .replaceQuery(params);
        URI uri = URI.create(uriBuilder.toString());
        LOG.debug("Constructed URI: {}", uri);
        return uri;
    }

    /**
     * Makes a call to the OTP server plan end point and convert. The original response and status are wrapped in a
     * single object and returned. It will fail if a connection is not made.
     */
    private OtpDispatcherResponse call(URI uri) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS))
                .GET()
                .build();

        OtpDispatcherResponse otpDispatcherResponse = null;

        // get plan response from OTP
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
