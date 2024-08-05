package org.opentripplanner.middleware.otp.graphql;

/** Wraps an OTP GraphQL query data. Field names below are defined by OTP. */
public class Query {
    public String query;

    public QueryVariables variables;
}
