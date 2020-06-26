package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.opentripplanner.middleware.bugsnag.response.App;
import org.opentripplanner.middleware.bugsnag.response.EventException;

import java.util.Date;
import java.util.List;

/**
 * Represents a Bugsnag error event. Class is used for both Mongo storage and JSON deserialization
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BugsnagEvent extends Model {

    @JsonProperty("id")
    public String eventDataId;
    @JsonProperty("project_id")
    public String projectId;
    @JsonProperty("error_id")
    public String errorId;
    @JsonProperty("received_at")
    public Date receivedAt;
    @JsonProperty("exceptions")
    public List<EventException> exceptions = null;
    @JsonProperty("severity")
    public String severity;
    @JsonProperty("context")
    public String context;
    @JsonProperty("unhandled")
    public Boolean unhandled;
    @JsonProperty("app")
    public App app;

    /** This no-arg constructor exists to make MongoDB happy. */
    public BugsnagEvent() {
    }

}
