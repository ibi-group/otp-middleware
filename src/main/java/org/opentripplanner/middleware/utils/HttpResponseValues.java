package org.opentripplanner.middleware.utils;

import org.apache.http.HttpResponse;

/**
 * A class to hold the response from an http request.
 */
public class HttpResponseValues {

    /** The original http response. The connection will be closed by this point, therefore, only methods that do not
     * require a connection will work. */
    public final HttpResponse originalClosedResponse;
    /** The contents of the response body if available, null otherwise. */
    public final String responseBody;
    /** The response status. */
    public final int status;

    HttpResponseValues(HttpResponse response, String responseBody) {
        this.originalClosedResponse = response;
        this.responseBody = responseBody;
        this.status = response.getStatusLine().getStatusCode();
    }
}
