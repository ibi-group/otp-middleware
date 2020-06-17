package org.opentripplanner.middleware.otp.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a trip planner response
 *
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {

    /** A dictionary of the parameters provided in the request that triggered this response. */
    public HashMap<String, String> requestParameters;
    public TripPlan plan;
    public PlannerError error = null;

    @Override
    public String toString() {
        return "Response{" +
                "requestParameters=" + requestParameters +
                ", plan=" + plan +
                ", error=" + error +
                '}';
    }
}