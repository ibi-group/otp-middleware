package org.opentripplanner.middleware.utils;

import org.eclipse.jetty.http.HttpMethod;
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
     * Location of the GraphQL plan query template file, as URI resource.
     */
    private static final String PLAN_QUERY_RESOURCE_URI =
        getConfigPropertyAsText("PLAN_QUERY_RESOURCE_URI", null);


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
            LOG.error("Unable to retrieve plan query from resource: {}.", GraphQLUtils.PLAN_QUERY_RESOURCE_URI);
            return null;
        }
        return rawPlanQuery.replace("\"", "\\\"");
    }

    /**
     * Download the plan query from resource URI.
     */
    static String getPlanQueryFromResource() {
        if (GraphQLUtils.PLAN_QUERY_RESOURCE_URI == null) {
            LOG.error("The plan query resource URI parameter \"PLAN_QUERY_RESOURCE_URI\" is undefined.");
            return null;
        }
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