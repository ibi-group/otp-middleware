package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse;
import static spark.Spark.halt;

public class JsonUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JsonUtils.class);
    private static Gson gson = new Gson();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Serialize an object into a JSON string representation.
     * TODO: Replace with use of objectmapper?
     */
    public static String toJson(Object object) {
        return gson.toJson(object);
    }

    /**
     * Wrapper around Spark halt method that formats message as JSON using {@link #formatJSON}.
     */
    public static void logMessageAndHalt(Request request, int statusCode, String message) throws HaltException {
        logMessageAndHalt(request, statusCode, message, null);
    }

    /** Utility method to parse generic object from Spark request body. */
    public static <T> T getPOJOFromRequestBody(Request req, Class<T> clazz) {
        try {
            // TODO: Use Jackson instead? If we opt for Jackson, we must change JsonUtils#toJson to also use Jackson.
            return gson.fromJson(req.body(), clazz);
        } catch (Exception e) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Error parsing JSON for " + clazz.getSimpleName(), e);
            throw e;
        }
    }

    /** Utility method to parse generic object from Http response. */
    public static <T> T getPOJOFromHttpResponse(HttpResponse<String> response, Class<T> clazz) {
        T pojo = null;
        try {
            pojo = mapper.readValue(response.body(), clazz);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to get POJO from http response", e);
        }
        return pojo;
    }

    /** Utility method to parse generic object from JSON String. */
    public static <T> T getPOJOFromJSON(String json, Class<T> clazz) {
        T pojo = null;
        try {
            pojo = mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            LOG.error("Unable to get POJO from json", e);
        }
        return pojo;
    }

    /**
     * Wrapper around Spark halt method that formats message as JSON using {@link #formatJSON}.
     * Extra logic occurs for when the status code is >= 500.  A Bugsnag report is created if
     * Bugsnag is configured.
     */
    public static void logMessageAndHalt(
        Request request,
        int statusCode,
        String message,
        Exception e
    ) throws HaltException {
        // Note that halting occurred, also print error stacktrace if applicable
        if (e != null) e.printStackTrace();
        LOG.info("Halting with status code {}.  Error message: {}", statusCode, message);

        if (statusCode >= 500) {
            LOG.error(message);
            // TODO Add bugsnag?
        }
        JsonNode json = getObjectNode(message, statusCode, e);
        halt(statusCode, json.toString());
    }

    /**
     * Constructs a JSON string with a result (i.e., OK or ERR), message, code, and if the exception argument is
     * supplied details about the exception encountered.
     */
    public static String formatJSON(String message, int code, Exception e) {
        return getObjectNode(message, code, e).toString();
    }

    /**
     * Constructs a JSON string containing the provided key/value pair.
     */
    public static String formatJSON (String key, String value) {
        return mapper.createObjectNode()
            .put(key, value)
            .toString();
    }

    /**
     * Constructs an object node with a result (i.e., OK or ERR), message, code, and if the exception argument is
     * supplied details about the exception encountered.
     */
    public static ObjectNode getObjectNode(String message, int code, Exception e) {
        String detail = e != null ? e.getMessage() : null;
        return mapper.createObjectNode()
            .put("result", code >= 400 ? "ERR" : "OK")
            .put("message", message)
            .put("code", code)
            .put("detail", detail);
    }
}
