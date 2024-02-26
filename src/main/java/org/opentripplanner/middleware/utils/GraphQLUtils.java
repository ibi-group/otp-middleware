package org.opentripplanner.middleware.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class GraphQLUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GraphQLUtils.class);

    // Lazily-initialized in getSchema()
    private static String schema = null;

    /**
     * Location of the GraphQL plan file, as Java resource.
     */
    public static final String GRAPHQL_RESOURCE = "queries/planQuery.graphql";

    /**
     * Return the full GraphQL plan file schema in Java string format, with {@code "} as {@code \"}
     */
    public static String getSchema() {
        if (GraphQLUtils.schema == null) {
            GraphQLUtils.schema = schemaAsString(GRAPHQL_RESOURCE);
	}
        return GraphQLUtils.schema;
    }

    /**
     * Return a GraphQL schema in Java string format, with {@code "} as {@code \"}
     * @param resource the plan file or any GraphQL file
     */
    static String schemaAsString(String resource) {
        StringBuilder builder = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(
            GraphQLUtils.class.getClassLoader().getResourceAsStream(resource)
        ))) {
            int value;
            // All this low-level stuff is just to put a \ in front of " in the string.
            while ((value = reader.read()) != -1) {
                if ((char)value == '\n') builder.append("\\n");
                else if ((char)value == '"') builder.append("\\\"");
                else builder.append((char) value);
            }
        } catch (Exception e) {
            LOG.error("Can't find \"{}\" resource.", resource, e);
        }
        return builder.toString();
    }
}
