package org.opentripplanner.middleware.utils.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "project_id",
    "error_class",
    "message",
    "context",
    "severity",
    "original_severity",
    "overridden_severity",
    "events",
    "events_url",
    "unthrottled_occurrence_count",
    "users",
    "first_seen",
    "last_seen",
    "first_seen_unfiltered",
    "last_seen_unfiltered",
    "status",
    "created_issue",
    "reopen_rules",
    "assigned_collaborator_id",
    "comment_count",
    "missing_dsyms",
    "release_stages",
    "grouping_reason",
    "grouping_fields",
    "url",
    "project_url"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectError {

    @JsonProperty("id")
    public String id;
    @JsonProperty("project_id")
    public String projectId;
    @JsonProperty("error_class")
    public String errorClass;
    @JsonProperty("message")
    public String message;
    @JsonProperty("context")
    public String context;
    @JsonProperty("severity")
    public String severity;
    @JsonProperty("original_severity")
    public String originalSeverity;
    @JsonProperty("overridden_severity")
    public Object overriddenSeverity;
    @JsonProperty("events")
    public Integer events;
    @JsonProperty("events_url")
    public String eventsUrl;
    @JsonProperty("unthrottled_occurrence_count")
    public Integer unthrottledOccurrenceCount;
    @JsonProperty("users")
    public Integer users;
    @JsonProperty("first_seen")
    public Date firstSeen;
    @JsonProperty("last_seen")
    public Date lastSeen;
    @JsonProperty("first_seen_unfiltered")
    public Date firstSeenUnfiltered;
    @JsonProperty("last_seen_unfiltered")
    public Date lastSeenUnfiltered;
    @JsonProperty("status")
    public String status;
    @JsonProperty("reopen_rules")
    public Object reopenRules;
    @JsonProperty("assigned_collaborator_id")
    public Object assignedCollaboratorId;
    @JsonProperty("comment_count")
    public Integer commentCount;
    @JsonProperty("missing_dsyms")
    public List<Object> missingDsyms = null;
    @JsonProperty("release_stages")
    public List<String> releaseStages = null;
    @JsonProperty("grouping_reason")
    public String groupingReason;
    @JsonProperty("grouping_fields")
    public GroupingFields groupingFields;
    @JsonProperty("url")
    public String url;
    @JsonProperty("project_url")
    public String projectUrl;

    @Override
    public String toString() {
        return "Error{" +
            "id='" + id + '\'' +
            ", projectId='" + projectId + '\'' +
            ", errorClass='" + errorClass + '\'' +
            ", message='" + message + '\'' +
            ", context='" + context + '\'' +
            ", severity='" + severity + '\'' +
            ", originalSeverity='" + originalSeverity + '\'' +
            ", overriddenSeverity=" + overriddenSeverity +
            ", events=" + events +
            ", eventsUrl='" + eventsUrl + '\'' +
            ", unthrottledOccurrenceCount=" + unthrottledOccurrenceCount +
            ", users=" + users +
            ", firstSeen='" + firstSeen + '\'' +
            ", lastSeen='" + lastSeen + '\'' +
            ", firstSeenUnfiltered='" + firstSeenUnfiltered + '\'' +
            ", lastSeenUnfiltered='" + lastSeenUnfiltered + '\'' +
            ", status='" + status + '\'' +
            ", reopenRules=" + reopenRules +
            ", assignedCollaboratorId=" + assignedCollaboratorId +
            ", commentCount=" + commentCount +
            ", missingDsyms=" + missingDsyms +
            ", releaseStages=" + releaseStages +
            ", groupingReason='" + groupingReason + '\'' +
            ", groupingFields=" + groupingFields +
            ", url='" + url + '\'' +
            ", projectUrl='" + projectUrl + '\'' +
            '}';
    }
}

