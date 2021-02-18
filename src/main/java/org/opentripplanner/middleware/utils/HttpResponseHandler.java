package org.opentripplanner.middleware.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * Extracts the required values from an {@link HttpResponse} and populates an instance of {@link HttpResponseValues}.
 */
public class HttpResponseHandler implements ResponseHandler<HttpResponseValues> {
    private final HttpUriRequest request;

    /**
     * Construct the handler with the incoming {@link HttpUriRequest} for access to request fields if needed.
     */
    public HttpResponseHandler(HttpUriRequest request) {
        this.request = request;
    }

    /**
     * Populates the {@link HttpResponseValues} with required values. Once this method completes the http connection
     * is closed.
     */
    public HttpResponseValues handleResponse(final HttpResponse response) {
        return new HttpResponseValues(request, response);
    }
}
