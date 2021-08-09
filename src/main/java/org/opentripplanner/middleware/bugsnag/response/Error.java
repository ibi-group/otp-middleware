package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Error {
    /** The error which this event is associated with */
    @JsonProperty("errorId")
    public String errorId;

    /** The date/time Bugsnag received the event */
    @JsonProperty("receivedAt")
    public Date receivedAt;

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

}
