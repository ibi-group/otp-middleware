package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static spark.Spark.halt;

public class JsonUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Serialize an object into a JSON string representation.
     */
    public static String toJson(Object object) {

        String json;
        try {
            json = mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            json = formatJSON(String.format("Unable to serialize object: %s.", object.toString()), 500, e);
        }

        return json;
    }

    /**
     * Wrapper around Spark halt method that formats message as JSON using {@link #formatJSON}.
     */
    public static void logMessageAndHalt(Request request, int statusCode, String message) throws HaltException {
        logMessageAndHalt(request, statusCode, message, null);
    }

    /** Utility method to parse generic object from Spark request body. */
    public static <T> T getPOJOFromRequestBody(Request req, Class<T> clazz) throws JsonProcessingException {
        return mapper.readValue(req.body(), clazz);
    }

    /** Utility method to parse generic object from JSON String. */
    public static <T> T getPOJOFromJSON(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            BugsnagReporter.reportErrorToBugsnag(
                String.format("Unable to get POJO from json for %s", clazz.getSimpleName()),
                json,
                e
            );
        }
        return null;
    }

    /**
     * Utility method to parse generic objects from JSON String and return as list
     */
    public static <T> List<T> getPOJOFromJSONAsList(String json, Class<T> clazz) {
        try {
            JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            BugsnagReporter.reportErrorToBugsnag(
                String.format("Unable to get POJO List from json for %s", clazz.getSimpleName()),
                json,
                e
            );
        }
        return null;
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
        int index = e == null ? 3 : 2;
        // Note that halting occurred, also print error stacktrace if applicable
        LOG.error(
            "Halting {} with status code {}.  Error message: {}\n halt originated at {}",
            request != null ? request.uri() : "[unknown URI]",
            statusCode,
            message,
            // Log a stack trace for the method calling logMessageAndHalt.
            Arrays.stream(Thread.currentThread().getStackTrace())
                .map(StackTraceElement::toString)
                .limit(8)
                .collect(joining("\n")),
            e
        );

        if (statusCode >= 500) {
            BugsnagReporter.reportErrorToBugsnag(message, e);
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
