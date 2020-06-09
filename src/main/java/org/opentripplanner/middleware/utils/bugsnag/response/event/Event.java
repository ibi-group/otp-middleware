package org.opentripplanner.middleware.utils.bugsnag.response.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "url",
    "project_url",
    "is_full_report",
    "error_id",
    "received_at",
    "exceptions",
    "severity",
    "context",
    "unhandled",
    "app"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {

    @JsonProperty("id")
    public String id;
    @JsonProperty("url")
    public String url;
    @JsonProperty("project_url")
    public String projectUrl;
    @JsonProperty("is_full_report")
    public Boolean isFullReport;
    @JsonProperty("error_id")
    public String errorId;
    @JsonProperty("received_at")
    public String receivedAt;
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

    @Override
    public String toString() {
        return "Event{" +
            "id='" + id + '\'' +
            ", url='" + url + '\'' +
            ", projectUrl='" + projectUrl + '\'' +
            ", isFullReport=" + isFullReport +
            ", errorId='" + errorId + '\'' +
            ", receivedAt='" + receivedAt + '\'' +
            ", exceptions=" + exceptions +
            ", severity='" + severity + '\'' +
            ", context='" + context + '\'' +
            ", unhandled=" + unhandled +
            ", app=" + app +
            '}';
    }
}