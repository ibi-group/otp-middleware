package org.opentripplanner.middleware.utils;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import spark.Filter;
import spark.Request;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class HttpUtils {

    public enum REQUEST_METHOD {GET, POST, DELETE}

    /**
     * A constant for a list of MIME types containing application/json only.
     */
    public static final List<String> JSON_ONLY = List.of(APPLICATION_JSON);

    /**
     * A constant method compliant with {@link spark.Filter} that does nothing,
     * but that is needed as a parameter for the spark-swagger endpoint definition method.
     */
    public static final Filter NO_FILTER = (request, response) -> {};

    /**
     * Constructs a url based on the uri.  endpoint and query params if provided
     */
    public static URI buildUri(String uri, String endpoint, String queryParams) {
        UriBuilder uriBuilder = UriBuilder.fromUri(uri);

        if (endpoint != null) {
            uriBuilder.path(endpoint);
        }

        if (queryParams != null) {
            uriBuilder.replaceQuery(queryParams);
        }
        return URI.create(uriBuilder.toString());
    }

    /**
     * Makes an http get/post request and returns the response body. The request is based on the provided params.
     */
    public static String httpRequest(URI uri, int connectionTimeout, REQUEST_METHOD method,
                                     HashMap<String, String> headers, String bodyContent) {

        return httpRequestRawResponse(uri, connectionTimeout, method, headers, bodyContent).body();
    }

    /**
     * Makes an http get/post request and returns the response. The request is based on the provided params.
     */
    public static HttpResponse<String> httpRequestRawResponse(URI uri, int connectionTimeout, REQUEST_METHOD method,
                                                              HashMap<String, String> headers, String bodyContent) {


        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(connectionTimeout))
            .GET();

        if (method.equals(REQUEST_METHOD.GET)) {
            httpRequestBuilder.GET();
        }

        if (method.equals(REQUEST_METHOD.DELETE)) {
            httpRequestBuilder.DELETE();
        }

        if (method.equals(REQUEST_METHOD.POST)) {
            httpRequestBuilder.POST(HttpRequest
                .BodyPublishers
                .ofString(bodyContent));
        }

        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                httpRequestBuilder.setHeader(e.getKey(), e.getValue());
            }
        }

        HttpRequest request = httpRequestBuilder.build();

        try {
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            BugsnagReporter.reportErrorToBugsnag("Error requesting data from URI", uri, e);
        }

        return null;
    }

    /**
     * Get entity attribute value from request. If nulls are not allowed, halt with error message.
     */
    public static String getRequiredQueryParamFromRequest(Request req, String paramName, boolean allowNull) {
        String paramValue = req.queryParams(paramName);
        if (paramValue == null && !allowNull) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                String.format("The parameter name (%s) must be provided.", paramName));
        }
        return paramValue;
    }

    /**
     * Get a request parameter value.
     * This method will halt the request if paramName is not provided in the request.
     */
    public static String getRequiredParamFromRequest(Request req, String paramName) {
        String paramValue = req.params(paramName);
        if (paramValue == null) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                String.format("The parameter name (%s) must be provided.", paramName));
        }
        return paramValue;
    }

}
