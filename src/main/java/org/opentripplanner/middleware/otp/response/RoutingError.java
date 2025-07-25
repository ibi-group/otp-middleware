package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This API response element represents an error in trip planning.
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutingError {

    public int id;
    public String code;
    public String description;
    public String inputField;

    public RoutingError() {
    }
}
