package org.opentripplanner.middleware.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Extracts the required values from an {@link HttpResponse} and populates an instance of {@link HttpResponseValues}.
 */
public class HttpResponseHandler implements ResponseHandler<HttpResponseValues> {
    private static final Logger LOG = LoggerFactory.getLogger(HttpResponseHandler.class);

    /**
     * Populates the {@link HttpResponseValues} with required values. Once this method completes the http connection
     * is closed.
     */
    public HttpResponseValues handleResponse(final HttpResponse response) {
        return new HttpResponseValues(response, getResponseBodyAsString(response));
    }

    /**
     * Extract the response body as a String. If an exception occurs return null.
     */
    private static String getResponseBodyAsString(HttpResponse response) {
        String responseBody = null;
        try {
            responseBody = EntityUtils.toString(response.getEntity());
            EntityUtils.consume(response.getEntity());
        } catch (IOException e) {
            LOG.error("Unable to get response body", e);
        }
        return responseBody;
    }

}
