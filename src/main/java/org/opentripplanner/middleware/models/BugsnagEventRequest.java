package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Bugsnag event request. Class is used for both Mongo storage and JSON deserializing
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BugsnagEventRequest extends Model {
    @JsonProperty("id")
    public String eventDataRequestId;
    public String status;
    public int total;
    public String url;

    /** This no-arg constructor exists to make MongoDB happy. */
    public BugsnagEventRequest() {
    }

}
