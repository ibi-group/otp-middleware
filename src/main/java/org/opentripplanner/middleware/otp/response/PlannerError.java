package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * This API response element represents an error in trip planning.
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlannerError {

    public int id;
    public String msg;
    public List<String> missing = null;
    public boolean noPath = false;

    @Override
    public String toString() {
        return "PlannerError{" +
                "id=" + id +
                ", msg='" + msg + '\'' +
                ", missing=" + missing +
                ", noPath=" + noPath +
                '}';
    }
}
