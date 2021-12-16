package org.opentripplanner.middleware.utils;

import io.github.manusant.ss.model.RefModel;
import io.github.manusant.ss.model.Response;

import java.util.LinkedHashMap;
import java.util.Map;

public class SwaggerUtils {
    private SwaggerUtils() {}

    /**
     * Creates the documentation for standard responses for most API endpoints.
     */
    public static Map<String, Response> createStandardResponses(Class<?> clazz) {
        Map<String, Response> responses = createStandardResponses();

        // Show the output data structure for the 200-ok response.
        RefModel refModel = new RefModel();
        refModel.set$ref(clazz.getSimpleName());
        responses.get("200").setResponseSchema(refModel);

        return responses;
    }

    /**
     * Creates the documentation for standard responses for most API endpoints.
     */
    public static Map<String, Response> createStandardResponses() {
        Map<String, Object> emptyExamples = new LinkedHashMap<>();

        Map<String, Response> responses = Map.of(
            "200", new Response().description("Successful operation"),
            "400", new Response().description("The request was not formed properly " +
                    "(e.g., some required parameters may be missing). " +
                    "See the details of the returned response to determine the exact issue."),
            "401", new Response().description("The server was not able to authenticate the request. " +
                    "This can happen if authentication headers are missing or malformed, " +
                    "or the authentication server cannot be reached."),
            "403", new Response().description("The requesting user is not allowed to perform the request."),
            "404", new Response().description("The requested item was not found."),
            "500", new Response().description("An error occurred while performing the request. " +
                    "Contact an API administrator for more information.")
        );

        // All responses get no examples, so that no extra docs are generated for error cases.
        // (spark-swagger will still generate a basic example for the 200 response.)
        responses.values().forEach(r -> r.setExamples(emptyExamples));

        return responses;
    }
}
