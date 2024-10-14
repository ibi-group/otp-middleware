package org.opentripplanner.middleware.otp.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wraps an OTP GraphQL query data.
 * Field names below are defined by OTP, except for batchId which helps group OTP plan requests for a particular search.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Query {
    public String batchId;

    public String query;

    public QueryVariables variables;
}
