package org.opentripplanner.middleware.utils;

import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class HttpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    /**
     * Constructs a url based on the uri, endpoint and query params if provided
     */
    public static URI buildUri(String uri, String endPoint, String queryParams) {
        UriBuilder uriBuilder = UriBuilder.fromUri(uri).path(endPoint);
        if (queryParams != null) {
            uriBuilder.replaceQuery(queryParams);
        }
        return URI.create(uriBuilder.toString());
    }

    public static <T> T callWithAuthToken(URI uri, Class<T> responseClazz, String token) {


        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(5))
            .setHeader("Authorization", "token " + token)
            .setHeader("Accept", "application/json; version=2")
            .GET()
            .build();

        try {
            // raw response
            HttpResponse<String> httpResponse = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            // convert raw response into concrete POJOs
            return JsonUtils.getPOJOFromHttpResponse(httpResponse, responseClazz);
        } catch (InterruptedException | IOException e) {
            LOG.error("Error requesting data from server", e);
        }
        return null;
    }


    /**
     * Get entity attribute value from request. If nulls are not allowed, halt with error message.
     */
    public static String getRequiredParamFromRequest(Request req, String paramName, boolean allowNull) {
        String paramValue = req.queryParams(paramName);
        if (paramValue == null && !allowNull) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "The parameter name " + paramName + " must be provided.");
        }
        return paramValue;
    }
}
