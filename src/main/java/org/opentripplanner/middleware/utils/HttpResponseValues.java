package org.opentripplanner.middleware.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * A class to hold elements of HTTP request/response pair (e.g., status, response body).
 */
public class HttpResponseValues {
    private static final Logger LOG = LoggerFactory.getLogger(HttpResponseValues.class);
    /**
     * The original http response. The connection will be closed by this point, therefore, only methods that do not
     * require a connection will work.
     */
    public final HttpResponse originalClosedResponse;
    /** The contents of the response body if available, null otherwise. */
    public final String responseBody;
    /** The response status. */
    public final int status;
    public final URI uri;

    /**
     * Build the response values from the original HTTP request and the resulting response.
     */
    HttpResponseValues(HttpUriRequest httpUriRequest, HttpResponse response) {
        this.uri = httpUriRequest.getURI();
        this.responseBody = getResponseBodyAsString(response);
        this.status = response.getStatusLine().getStatusCode();
        this.originalClosedResponse = response;
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
