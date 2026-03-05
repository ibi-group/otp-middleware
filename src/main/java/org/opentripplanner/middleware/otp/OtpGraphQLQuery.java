package org.opentripplanner.middleware.otp;

/** Wraps an OTP GraphQL query data */
public class OtpGraphQLQuery<T> {
    public String query;

    public T variables;
}
