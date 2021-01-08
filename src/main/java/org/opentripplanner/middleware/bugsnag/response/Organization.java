package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * A bugsnag organization. Information relating to this class can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations
 *
 * The organizations are pulled only once upon application start. All dynamic parameters have been removed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization {

    /** Organization Bugsnag id */
    public String id;

    /** Organization name */
    public String name;

    /** {@link Creator} of organization */
    public Creator creator;

    /** URL referencing an organization's collaborators */
    @JsonProperty("collaborators_url")
    public String collaboratorsUrl;

    /** URL referencing an organization's {@link BugsnagProject}s */
    @JsonProperty("projects_url")
    public String projectsUrl;

    /** Organization create date/time */
    @JsonProperty("created_at")
    public Date createdAt;
}
