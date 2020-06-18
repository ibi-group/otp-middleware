package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "slug",
    "name",
    "api_key",
    "type",
    "is_full_view",
    "release_stages",
    "language",
    "created_at",
    "updated_at",
    "errors_url",
    "events_url",
    "url",
    "html_url",
    "open_error_count",
    "for_review_error_count",
    "collaborators_count",
    "global_grouping",
    "location_grouping",
    "discarded_app_versions",
    "discarded_errors",
    "custom_event_fields_used",
    "resolve_on_deploy",
    "url_whitelist",
    "ignore_old_browsers",
    "ignored_browser_versions"
})
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

    @Override
    public String toString() {
        return "Project{" +
            "id='" + id + '\'' +
            ", slug='" + slug + '\'' +
            ", name='" + name + '\'' +
            ", apiKey='" + apiKey + '\'' +
            ", type='" + type + '\'' +
            ", isFullView=" + isFullView +
            ", releaseStages=" + releaseStages +
            ", language='" + language + '\'' +
            ", createdAt='" + createdAt + '\'' +
            ", updatedAt='" + updatedAt + '\'' +
            ", errorsUrl='" + errorsUrl + '\'' +
            ", eventsUrl='" + eventsUrl + '\'' +
            ", url='" + url + '\'' +
            ", htmlUrl='" + htmlUrl + '\'' +
            ", openErrorCount=" + openErrorCount +
            ", forReviewErrorCount=" + forReviewErrorCount +
            ", collaboratorsCount=" + collaboratorsCount +
            ", globalGrouping=" + globalGrouping +
            ", locationGrouping=" + locationGrouping +
            ", discardedAppVersions=" + discardedAppVersions +
            ", discardedErrors=" + discardedErrors +
            ", customEventFieldsUsed=" + customEventFieldsUsed +
            ", resolveOnDeploy=" + resolveOnDeploy +
            ", urlWhitelist=" + urlWhitelist +
            ", ignoreOldBrowsers=" + ignoreOldBrowsers +
            '}';
    }
}