package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.opentripplanner.middleware.bugsnag.response.App;
import org.opentripplanner.middleware.bugsnag.BugsnagWebHookDelivery;
import org.opentripplanner.middleware.bugsnag.response.EventException;

import java.util.Date;
import java.util.List;

/**
 * Represents a Bugsnag event. This class is used for both Mongo storage and JSON deserialization.
 * Information relating to this can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/projects/event-data-requests/create-an-event-data-request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BugsnagEvent extends Model {

    /** Event data id */
    @JsonProperty("id")
    public String eventDataId;

    /** The project which this event is associated with */
    @JsonProperty("project_id")
    public String projectId;

    /** The error which this event is associated with */
    @JsonProperty("error_id")
    public String errorId;

    /** The date/time Bugsnag received the event */
    @JsonProperty("received_at")
    public Date receivedAt;

    /** A list of {@link EventException} classes and messages associated with this event */
    @JsonProperty("exceptions")
    public List<EventException> exceptions = null;

    /** The event severity, warn, error etc */
    @JsonProperty("severity")
    public String severity;

    /** The location within the reporting application where the event was flagged */
    @JsonProperty("context")
    public String context;

    /** Defines an unhandled exception */
    @JsonProperty("unhandled")
    public Boolean unhandled;

    /** An {@link App} which contains information relating to the environment the app is running under */
    @JsonProperty("app")
    public App app;

    /** This no-arg constructor exists to make MongoDB happy. */
    public BugsnagEvent() {
    }

    /**
     * Create a Bugsnag event from a Bugsnag webhook delivery. EventDataId and exceptions are not available via the
     * webhook. In order to provide a unique reference the eventDataId will be a combination of project and error ids.
     */
    public BugsnagEvent(BugsnagWebHookDelivery bugsnagWebHookDelivery) {
        // Create unique eventDataId reference.
        this.eventDataId = String.format("%s-%s", bugsnagWebHookDelivery.project.id, bugsnagWebHookDelivery.error.errorId);
        this.projectId = bugsnagWebHookDelivery.project.id;
        this.errorId = bugsnagWebHookDelivery.error.errorId;
        this.receivedAt = bugsnagWebHookDelivery.error.receivedAt;
        this.severity = bugsnagWebHookDelivery.error.severity;
        this.context = bugsnagWebHookDelivery.error.context;
        this.unhandled = bugsnagWebHookDelivery.error.unhandled;
        this.app = bugsnagWebHookDelivery.error.app;
    }
}
