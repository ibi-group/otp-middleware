package org.opentripplanner.middleware.otp.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This and all child classes are (were possible) copies of classes (with matching names) from open trip planner.
 * They are used to deserialize a plan response from OTP.
 *
 * Represents a trip planner response, will be serialized into XML or JSON by Jersey
 *
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {

    /** A dictionary of the parameters provided in the request that triggered this response. */
    public HashMap<String, String> requestParameters;
    private TripPlan plan;
    private PlannerError error = null;

    /** This no-arg constructor exists to make JAX-RS happy. */
    @SuppressWarnings("unused")
    private Response() {}

    /** Construct an new response initialized with all the incoming query parameters. */
    public Response(UriInfo info) {
        this.requestParameters = new HashMap<>();
        if (info == null) {
            // in tests where there is no HTTP request, just leave the map empty
            return;
        }
        for (Entry<String, List<String>> e : info.getQueryParameters().entrySet()) {
            // include only the first instance of each query parameter
            requestParameters.put(e.getKey(), e.getValue().get(0));
        }
    }

    /** The actual trip plan. */
    public TripPlan getPlan() {
        return (plan == null) ? null : plan;
    }

    public void setPlan(TripPlan plan) {
        this.plan = plan;
    }

    /** The error (if any) that this response raised. */
    public PlannerError getError() {
        return error;
    }

    public void setError(PlannerError error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "Response{" +
                "requestParameters=" + requestParameters +
                ", plan=" + plan +
                ", error=" + error +
                '}';
    }
}