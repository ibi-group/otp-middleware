package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    @JsonProperty("id")
    public String id;
    @JsonProperty("slug")
    public String slug;
    @JsonProperty("name")
    public String name;
    @JsonProperty("api_key")
    public String apiKey;
    @JsonProperty("type")
    public String type;
    @JsonProperty("is_full_view")
    public Boolean isFullView;
    @JsonProperty("release_stages")
    public List<String> releaseStages = null;
    @JsonProperty("language")
    public String language;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("updated_at")
    public String updatedAt;
    @JsonProperty("errors_url")
    public String errorsUrl;
    @JsonProperty("events_url")
    public String eventsUrl;
    @JsonProperty("url")
    public String url;
    @JsonProperty("html_url")
    public String htmlUrl;
    @JsonProperty("open_error_count")
    public Integer openErrorCount;
    @JsonProperty("for_review_error_count")
    public Integer forReviewErrorCount;
    @JsonProperty("collaborators_count")
    public Integer collaboratorsCount;
    @JsonProperty("global_grouping")
    public List<Object> globalGrouping = null;
    @JsonProperty("location_grouping")
    public List<Object> locationGrouping = null;
    @JsonProperty("discarded_app_versions")
    public List<Object> discardedAppVersions = null;
    @JsonProperty("discarded_errors")
    public List<Object> discardedErrors = null;
    @JsonProperty("custom_event_fields_used")
    public Integer customEventFieldsUsed;
    @JsonProperty("resolve_on_deploy")
    public Boolean resolveOnDeploy;
    @JsonProperty("url_whitelist")
    public Object urlWhitelist;
    @JsonProperty("ignore_old_browsers")
    public Boolean ignoreOldBrowsers;

}