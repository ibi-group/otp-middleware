package org.opentripplanner.middleware.utils;

import org.eclipse.jetty.http.HttpMethod;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

public class GraphQLUtils {

    private GraphQLUtils() {
        throw new IllegalStateException("Utility class.");
    }

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLUtils.class);

    // Lazily-initialized in getPlanQueryTemplate()
    private static String planQueryTemplate = null;

    /**
     * Location of the GraphQL default plan query template file, as URI resource.
     */
    private static final String DEFAULT_PLAN_QUERY_RESOURCE_URI =
        "https://raw.githubusercontent.com/opentripplanner/otp-ui/refs/heads/master/packages/core-utils/src/planQuery.graphql";

    /**
     * Location of the GraphQL plan query template file, as URI resource.
     */
    private static final String PLAN_QUERY_RESOURCE_URI =
        getConfigPropertyAsText(
            "PLAN_QUERY_RESOURCE_URI",
            DEFAULT_PLAN_QUERY_RESOURCE_URI
        );


    /**
     * Return the full GraphQL plan file planQueryTemplate.
     */
    public static String getPlanQueryTemplate() {
        if (GraphQLUtils.planQueryTemplate == null) {
            GraphQLUtils.planQueryTemplate = planQueryTemplateAsString();
        }
        return GraphQLUtils.planQueryTemplate;
    }

    /**
     * Return a GraphQL planQueryTemplate in Java string format, with {@code "} as {@code \"}.
     */
    static String planQueryTemplateAsString() {
        String rawPlanQuery = getPlanQueryFromResource();
        if (rawPlanQuery == null) {
            String message = String.format("Unable to retrieve plan query from resource: %s.", GraphQLUtils.PLAN_QUERY_RESOURCE_URI);
            LOG.error(message);
            throw new IllegalStateException(message);
        }
        return rawPlanQuery.replace("\"", "\\\"");
    }

    /**
     * Download the plan query from resource URI.
     */
    static String getPlanQueryFromResource() {
        HttpResponseValues httpResponseValues = HttpUtils.httpRequestRawResponse(
            URI.create(GraphQLUtils.PLAN_QUERY_RESOURCE_URI),
            10,
            HttpMethod.GET,
            null,
            null
        );
        return httpResponseValues != null ? httpResponseValues.responseBody : null;
    }
}