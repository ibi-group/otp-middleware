package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.core.JsonProcessingException;
import joptsimple.internal.Strings;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.OtpLegResponseWrapper;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.List;
import java.util.function.Function;

import static org.opentripplanner.middleware.otp.OtpDispatcher.OTP_GRAPHQL_ENDPOINT;
import static org.opentripplanner.middleware.otp.OtpDispatcher.sendOtpPostRequest;

/**
 * Helper class to perform a leg query in OTP.
 */
public class LegFinder {
    private static final List<String> LEG_FIELDS = List.of(
        "id",
        "startTime",
        "endTime",
        "departureDelay",
        "arrivalDelay",
        "transitLeg"
    );
    public static final String LEG_QUERY = String.format(
        "query ($legId: String!) { leg(id: $legId) { %s } }",
        Strings.join(LEG_FIELDS, " ")
    );

    private final Function<String, OtpDispatcherResponse> legResponseProvider;

    public LegFinder(Function<String, OtpDispatcherResponse> legResponseProvider) {
        this.legResponseProvider = legResponseProvider;
    }

    public LegFinder() {
        this(LegFinder::sendOtpLegRequest);
    }

    public Leg queryLeg(String legId) {
        OtpDispatcherResponse dispResponse = legResponseProvider.apply(legId);
        if (dispResponse != null && dispResponse.statusCode < 400) {
            try {
                return JsonUtils.getPOJOFromJSON(dispResponse.responseBody, OtpLegResponseWrapper.class).data.leg;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * Provides OTP's response for the desired leg id.
     * Only the minimal fields needed to reconstruct an itinerary with real-time updates are included.
     */
    public static OtpDispatcherResponse sendOtpLegRequest(String legId) {
        OtpGraphQLQuery<LegQueryVariables> query = new OtpGraphQLQuery<>();
        query.query = LEG_QUERY;
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
}
