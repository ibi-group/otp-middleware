package org.opentripplanner.middleware.utils;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class HttpUtils {
    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

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
    public static URI buildUri(String uri, String... pathElements) {
        UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        if (pathElements != null) {
            // Turn path elements into string (filtering out any nulls).
            List<String> nonNullElements = Arrays.stream(pathElements)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            uriBuilder.path(String.join("/", nonNullElements));
        }
        return URI.create(uriBuilder.toString());
    }

    /**
     * Makes an http request (following redirects if triggered) and return the response.
     */
    public static HttpResponseValues httpRequestRawResponse(URI uri, int timeoutInSeconds, HttpMethod method,
                                                      Map<String, String> headers, String bodyContent) {
        return httpRequestRawResponse(uri, timeoutInSeconds, method, headers, bodyContent, true);
    }

    /**
     * Makes an http request and returns the response.
     */
    public static HttpResponseValues httpRequestRawResponse(URI uri, int timeoutInSeconds, HttpMethod method,
                                                      Map<String, String> headers, String bodyContent,
                                                      boolean allowRedirects) {
        int timeoutInMilliSeconds = timeoutInSeconds * 1000;
        RequestConfig timeoutConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(timeoutInMilliSeconds)
            .setConnectTimeout(timeoutInMilliSeconds)
            .setSocketTimeout(timeoutInMilliSeconds)
            .build();

        HttpUriRequest httpUriRequest;

        // Handle building requests for supported methods.
        switch (method) {
            case GET:
                HttpGet getRequest = new HttpGet(uri);
                getRequest.setConfig(timeoutConfig);
                httpUriRequest = getRequest;
                break;
            case DELETE:
                HttpDelete deleteRequest = new HttpDelete(uri);
                deleteRequest.setConfig(timeoutConfig);
                httpUriRequest = deleteRequest;
                break;
            case PUT:
                HttpPut putRequest = new HttpPut(uri);
                putRequest.setEntity(new StringEntity(bodyContent, "UTF-8"));
                putRequest.setConfig(timeoutConfig);
                httpUriRequest = putRequest;
                break;
            case POST:
                HttpPost postRequest = new HttpPost(uri);
                postRequest.setEntity(new StringEntity(bodyContent, "UTF-8"));
                postRequest.setConfig(timeoutConfig);
                httpUriRequest = postRequest;
                break;
            case HEAD:
            case OPTIONS:
            case TRACE:
            case CONNECT:
            case MOVE:
            case PROXY:
            case PRI:
            default:
                throw new IllegalArgumentException(String.format("HTTP method '%s' not currently supported!", method));
        }

        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                httpUriRequest.setHeader(e.getKey(), e.getValue());
            }
        }

        CloseableHttpClient httpClient = allowRedirects
            ? HttpClientBuilder.create().build()
            : HttpClientBuilder.create().disableRedirectHandling().build();

        try  {
            // Extract required information from the response and return to caller. The connection is closed once complete.
            return httpClient.execute(httpUriRequest, new HttpResponseHandler(httpUriRequest));
        } catch (HttpTimeoutException e) {
            LOG.error("Request to {} timed out after {} seconds.", uri, timeoutInSeconds, e);
        } catch (IOException e) {
            BugsnagReporter.reportErrorToBugsnag("Error requesting data from URI", uri, e);
        }
        return null;
    }

    /**
     * Get optional query param value from request as int (defaults to defaultValue). If parsed value is outside of the
     * range of accepted values, it will be pinned to the min or max value (depending on which end of the range it is
     * located).
     */
    public static int getQueryParamFromRequest(Request req, String name, int min, int defaultValue, int max) {
        // Start with default value
        int value = defaultValue;
        String requestValue = null;
        try {
            // Attempt to get value from query param.
            requestValue = HttpUtils.getQueryParamFromRequest(req, name, true);
            if (requestValue != null) {
                value = Integer.parseInt(requestValue);
                // If requested value is out of range, pin to min/max.
                if (value < min) value = min;
                else if (value > max) value = max;
            }
        } catch (NumberFormatException e) {
            LOG.warn("Unable to parse {} value of {}. Using default limit: {}", name, requestValue, defaultValue, e);
        }
        return value;
    }

    /**
     * Get query param value from request as int with no maximum value. If not optional, halt with error message.
     */
    public static int getQueryParamFromRequest(Request req, String name, int min, int defaultValue) {
        return getQueryParamFromRequest(req, name, min, defaultValue, Integer.MAX_VALUE);
    }

    /**
     * Get query param value from request as string. If not optional/nulls not allowed, halt with error message.
     */
    public static String getQueryParamFromRequest(Request req, String paramName, boolean optional) {
        String paramValue = req.queryParams(paramName);
        if (paramValue == null && !optional) {
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

    /**
     * Get date from request parameter and convert to {@link Date} at a specific time of day. The date conversion
     * is based on the system time zone.
     */
    public static Date getDate(Request request, String paramName, String paramValue, LocalTime timeOfDay) {

        // no date value to work with
        if (paramValue == null) {
            return null;
        }

        LocalDate localDate = null;
        try {
            localDate = DateTimeUtils.getDateFromParam(paramName, paramValue, DEFAULT_DATE_FORMAT_PATTERN);
        } catch (DateTimeParseException e) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400,
                String.format("%s value: %s is not a valid date. Must be in the format: %s", paramName, paramValue,
                    DEFAULT_DATE_FORMAT_PATTERN
                ));
        }

        if (localDate == null) {
            return null;
        }

        return Date.from(localDate.atTime(timeOfDay)
            .atZone(DateTimeUtils.getSystemZoneId())
            .toInstant());
    }
}
