package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

/**
 * Information relating to this class can be found here: https://bugsnagapiv2.docs.apiary.io/#reference/projects
 * The projects are pulled only once upon application start. All dynamic parameters has been removed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    /** Project id */
    @JsonProperty("id")
    public String id;

    /** Project name */
    @JsonProperty("name")
    public String name;

    /** The notifier API key used to configure the notifier library being used to report errors in the project */
    @JsonProperty("api_key")
    public String apiKey;

    /** Project framework, e.g. Java, PHP, JS etc */
    @JsonProperty("type")
    public String type;

    /** The Project's programming language as derived from the framework represented in the type field */
    @JsonProperty("language")
    public String language;

    /** The time of the Project's creation. */
    @JsonProperty("created_at")
    public Date createdAt;

    /** The API URL for the Project's Errors. */
    @JsonProperty("errors_url")
    public String errorsUrl;

    /** The API URL for the Project's Events. */
    @JsonProperty("events_url")
    public String eventsUrl;

    /** The API URL for the Project. */
    @JsonProperty("url")
    public String url;

    /** The dashboard URL for the project. */
    @JsonProperty("html_url")
    public String htmlUrl;

    /** A list of error classes. Events with these classes will be grouped by their class, regardless of the location
     * that they occur in the Project's source code. Altering a Project's global_grouping will not cause existing errors
     * to be regrouped.
     */
    @JsonProperty("global_grouping")
    public List<String> globalGrouping = null;

    /** A list of error classes. Events with these classes will be grouped by their context. Altering a Project's
     * location_grouping will not cause existing errors to be regrouped.
     */
    @JsonProperty("location_grouping")
    public List<String> locationGrouping = null;

    /** A list of app versions whose events will be discarded if received for the Project. */
    @JsonProperty("discarded_app_versions")
    public List<String> discardedAppVersions = null;

    /** If true, every error in the Project will be marked as 'fixed' after using the Deploy Tracking API to notify
     * Bugsnag of a new production deploy.
     */
    @JsonProperty("resolve_on_deploy")
    public Boolean resolveOnDeploy;

    /** Whether the Events in the Project will be ignored if they originate from old web browsers. Relevant to
     * JavaScript Projects only.
     */
    @JsonProperty("ignore_old_browsers")
    public Boolean ignoreOldBrowsers;

}