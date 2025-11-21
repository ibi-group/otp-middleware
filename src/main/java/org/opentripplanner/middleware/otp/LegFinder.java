package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.function.Function;

import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_GRAPHQL_ENDPOINT;
import static org.opentripplanner.middleware.otp.OtpDispatcher.sendOtpPostRequest;

/**
 * Helper class to perform a leg query in OTP.
 */
public class LegFinder {
    private final Function<String, OtpDispatcherResponse> legResponseProvider;

    public LegFinder(Function<String, OtpDispatcherResponse> legResponseProvider) {
        this.legResponseProvider = legResponseProvider;
    }

    public LegFinder() {
        this(LegFinder::sendOtpLegRequest);
    }

    private Leg queryLeg(String legId) {
        OtpDispatcherResponse dispResponse = legResponseProvider.apply(legId);
        if (dispResponse != null && dispResponse.statusCode < 400) {
            try {
                return JsonUtils.getPOJOFromJSON(dispResponse.responseBody, LegResponseWrapper.class).data.leg;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public boolean legExists(String legId) {
        return queryLeg(legId) != null;
    }

    /**
     * Provides OTP's response for the desired leg id.
     */
    // TODO: Adjust OtpDispatcherResponseType
    public static OtpDispatcherResponse sendOtpLegRequest(String legId) {
        OtpGraphQLQuery<LegQueryVariables> query = new OtpGraphQLQuery<>();
        query.query = "query ($legId: String!) { leg(id: $legId) { id } }";
        query.variables = new LegQueryVariables(legId);
        return sendOtpPostRequest(
            OtpVersion.OTP2,
            "",
            OTP_GRAPHQL_ENDPOINT,
            HttpUtils.HEADERS_JSON,
            JsonUtils.toJson(query).replace("\\\\n", "\\n").replace("\\\\\"", "\"")
        );
    }

    public static class LegQueryVariables {
        public final String legId;

        public LegQueryVariables(String legId) {
            this.legId = legId;
        }
    }

    // TODO: Combine with OtpResponse
    public static class LegResponseWrapper {
        public LegResponse data;
    }

    public static class LegResponse {
        public Leg leg;
    }
}
