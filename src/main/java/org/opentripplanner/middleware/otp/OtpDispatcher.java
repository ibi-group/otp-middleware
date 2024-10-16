package org.opentripplanner.middleware.otp;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.utils.GraphQLUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.function.Supplier;

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

    /**
     * Match the OTP GraphQL request timeout defined at
     * https://github.com/opentripplanner/OpenTripPlanner/blob/176e5f51923e82f8a4c2aa2a0b8284e1497b4439/src/main/java/org/opentripplanner/apis/gtfs/GtfsGraphQLAPI.java#L54
     */
    private static final int OTP_SERVER_REQUEST_TIMEOUT_IN_SECONDS = 30;

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
        return sendOtpPlanRequest(version, otpRequest.requestParameters);
    }

    /**
     * Provides a response from the OTP server target service based on the input {@link OtpRequest}.
     */
    public static OtpDispatcherResponse sendOtpPlanRequest(OtpVersion version, OtpGraphQLVariables params) {
        OtpGraphQLQuery query = new OtpGraphQLQuery();
        query.query = GraphQLUtils.getPlanQueryTemplate();
        query.variables = params;
        return sendOtpPostRequest(
            version,
            "",
            OTP_GRAPHQL_ENDPOINT,
            HttpUtils.HEADERS_JSON,
            JsonUtils.toJson(query).replace("\\\\n", "\\n").replace("\\\\\"", "\"")
        );
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
        LOG.info("Sending request to OTP: {}", uri);
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
        return handleOtpDispatcherResponse(() -> sendOtpPlanRequest(OtpVersion.OTP2, sentParams));
    }

    public static OtpResponse sendOtpRequestWithErrorHandling(OtpRequest otpRequest) {
        return handleOtpDispatcherResponse(() -> sendOtpPlanRequest(OtpVersion.OTP2, otpRequest));
    }

    public static OtpResponse sendOtpRequestWithErrorHandling(OtpGraphQLVariables params) {
        return handleOtpDispatcherResponse(() -> sendOtpPlanRequest(OtpVersion.OTP2, params));
    }

    private static OtpResponse handleOtpDispatcherResponse(Supplier<OtpDispatcherResponse> otpDispatcherResponseSupplier) {
        OtpDispatcherResponse otpDispatcherResponse;
        try {
            otpDispatcherResponse = otpDispatcherResponseSupplier.get();
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag(
                "Encountered an error while making a request to the OTP server.",
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
