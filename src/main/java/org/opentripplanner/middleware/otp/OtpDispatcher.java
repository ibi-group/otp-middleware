package org.opentripplanner.middleware.otp;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Responsible for constructing requests to an elected OTP server endpoint using original query parameters provided by
 * the requester. To provide the response from the OTP server in the form of status code and body so the correct
 * response can be provided to the requester.
 */
public class OtpDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(OtpDispatcher.class);

    /**
     * Location of the OTP plan endpoint (e.g. /routers/default/plan).
     */
    public static final String OTP_PLAN_ENDPOINT = getConfigPropertyAsText("OTP_PLAN_ENDPOINT", "/routers/default/plan");

    /**
     * Location of the OTP GraphQL endpoint (e.g. /routers/default/index/graphql).
     */
    public static final String OTP_GRAPHQL_ENDPOINT = getConfigPropertyAsText("OTP_GRAPHQL_ENDPOINT", "/routers/default/index/graphql");

    private static final int OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS = 10;

    /**
     * Provides a response from the OTP server target service based on the query parameters provided.
     */
    public static OtpDispatcherResponse sendOtpRequest(OtpVersion version, String query, String path) {
        LOG.debug("Original query string: {}", query);
        return sendOtpRequest(buildOtpUri(version, query, path));
    }

    /**
     * Sends a POST request to OTP where all the HTTP entities (path, query, headers, body) are
     * settable.
     */
    public static OtpDispatcherResponse sendOtpPostRequest(
            OtpVersion version,
            String query,
            String path,
            Map<String, String> headers,
            String bodyContent
    ) {
        LOG.debug("Original query string: {}", query);
        return sendOtpRequest(buildOtpUri(version, query, path), HttpMethod.POST, headers, bodyContent);
    }

    /**
     * Provides a response from the OTP server target service based on the query parameters provided.
     */
    public static OtpDispatcherResponse sendOtpPlanRequest(OtpVersion version, String query) {
        LOG.debug("Original query string: {}", query);
        return sendOtpRequest(buildOtpUri(version, query, OTP_PLAN_ENDPOINT));
    }

    /**
     * Provides a response from the OTP server target service based on the input {@link OtpRequest}.
     */
    public static OtpDispatcherResponse sendOtpPlanRequest(OtpVersion version, OtpRequest otpRequest) {
        return sendOtpPlanRequest(version, ItineraryUtils.toQueryString(otpRequest.requestParameters));
    }

    /**
     * Provides a response from the OTP server target service based on the query parameters provided. This is used only
     * during testing.
     */
    public static OtpDispatcherResponse sendOtpPlanRequest(OtpVersion version, String from, String to, String time) {
        return sendOtpPlanRequest(version, String.format("fromPlace=%s&toPlace=%s&time=%s", from, to, time));
    }

    /**
     * Constructs a URL based on the otp server URL and the requester's target service (e.g. plan) and query
     * parameters.
     */
    private static URI buildOtpUri(OtpVersion version, String params, String path) {
        UriBuilder uriBuilder = UriBuilder.fromUri(version.uri())
            .path(path)
            .replaceQuery(params);
        URI uri = URI.create(uriBuilder.toString());
        LOG.debug("Constructed URI: {}", uri);
        return uri;
    }

    /**
     * Simplified version of method that provides an easy interface if you don't care about setting
     * method, headers or body.
     */
    private static OtpDispatcherResponse sendOtpRequest(URI uri) {
       return sendOtpRequest(uri, HttpMethod.GET, null, null);
    }
    /**
     * Makes a call to the OTP server end point. The original response and status are wrapped in a single object and
     * returned. It will fail if a connection is not made.
     */
    private static OtpDispatcherResponse sendOtpRequest(
            URI uri,
            HttpMethod method,
            Map<String, String> headers,
            String bodyContent
    ) {
        LOG.info("Sending request to OTP: {}", uri.toString());
        HttpResponseValues otpResponse =
            HttpUtils.httpRequestRawResponse(
                uri,
                OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS,
                method,
                headers,
                bodyContent);
        return new OtpDispatcherResponse(otpResponse);
    }

    public static OtpResponse sendOtpRequestWithErrorHandling(String sentParams) {
        OtpDispatcherResponse otpDispatcherResponse;
        try {
            otpDispatcherResponse = sendOtpPlanRequest(OtpVersion.OTP1, sentParams);
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag(
                "Encountered an error while making a request ot the OTP server.",
                e
            );
            return null;
        }

        if (otpDispatcherResponse.statusCode >= 400) {
            BugsnagReporter.reportErrorToBugsnag(
                "Received an error from the OTP server.",
                otpDispatcherResponse,
                null
            );
            return null;
        }

        try {
            return otpDispatcherResponse.getResponse();
        } catch (JsonProcessingException e) {
            // don't report to Bugsnag since the getResponse method will already have reported to Bugsnag.
            LOG.error("Unable to parse OTP response!", e);
            return null;
        }
    }
}
