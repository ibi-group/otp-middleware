package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Wrapper for an OTP response for the GraphQL 'plan' query. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpResponseGraphQLWrapper {
    public OtpResponse data;
}