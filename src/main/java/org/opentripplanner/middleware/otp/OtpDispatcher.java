package org.opentripplanner.middleware.otp;

import java.util.Map;
import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.utils.GraphQLUtils;
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
     * Location of the OTP GraphQL endpoint (e.g. /gtfs/v1).
     */
    public static final String OTP_GRAPHQL_ENDPOINT = getConfigPropertyAsText("OTP_GRAPHQL_ENDPOINT", "/gtfs/v1");

    /**
     * Location of the OTP plan endpoint (e.g. /routers/default/plan).
     */
    public static final String OTP_PLAN_ENDPOINT = getConfigPropertyAsText("OTP_PLAN_ENDPOINT", "/routers/default/plan");
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
     * Send GraphQL POST request. Would ideally take a JSON Object, but the {@code "query"} object is
     * followed by a string of JSON-ish GraphQL and the {@code "variables"} object is a proper JSON
     * object of key/value string pairs so we put these together by hand.
     * @version OTP version passed along to post request
     * @variables a string of a JSON object of key/value string pairs
     */
    public static OtpDispatcherResponse sendGraphQLPostRequest(OtpVersion version, String variables) {
        var body = new StringBuilder("{\"query\":\"");
        body.append(GraphQLUtils.getSchema());
        body.append("\",\n\"variables\":");
        body.append(variables);
        body.append("}");
        return sendOtpPostRequest(version, "", OTP_GRAPHQL_ENDPOINT, HttpUtils.HEADERS_JSON, body.toString());
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
}
