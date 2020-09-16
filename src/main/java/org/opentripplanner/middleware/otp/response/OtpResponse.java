package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.util.HashMap;

/**
 * Represents a trip planner response
 *
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpResponse {

    /** A dictionary of the parameters provided in the request that triggered this response. */
    public HashMap<String, String> requestParameters;
    public TripPlan plan;
    public PlannerError error = null;
    /** A timestamp representing when the response was received */
    public long timestamp = DateTimeUtils.currentTimeMillis();

    @Override
    public String toString() {
        return "Response{" +
                "requestParameters=" + requestParameters +
                ", plan=" + plan +
                ", error=" + error +
                '}';
    }
}