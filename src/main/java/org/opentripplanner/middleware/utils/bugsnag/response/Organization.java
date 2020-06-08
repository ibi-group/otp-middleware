package org.opentripplanner.middleware.utils.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "name",
    "slug",
    "creator",
    "collaborators_url",
    "projects_url",
    "created_at",
    "updated_at",
    "auto_upgrade",
    "upgrade_url",
    "can_start_pro_trial",
    "pro_trial_ends_at",
    "pro_trial_feature",
    "billing_emails"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization {

    @JsonProperty("id")
    public String id;
    @JsonProperty("name")
    public String name;
    @JsonProperty("slug")
    public String slug;
    @JsonProperty("creator")
    public Creator creator;
    @JsonProperty("collaborators_url")
    public String collaboratorsUrl;
    @JsonProperty("projects_url")
    public String projectsUrl;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("updated_at")
    public String updatedAt;
    @JsonProperty("auto_upgrade")
    public Boolean autoUpgrade;
    @JsonProperty("upgrade_url")
    public String upgradeUrl;
    @JsonProperty("can_start_pro_trial")
    public Boolean canStartProTrial;
    @JsonProperty("pro_trial_ends_at")
    public Object proTrialEndsAt;
    @JsonProperty("pro_trial_feature")
    public Boolean proTrialFeature;
    @JsonProperty("billing_emails")
    public List<String> billingEmails = null;

    @Override
    public String toString() {
        return "Organization{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", slug='" + slug + '\'' +
            ", creator=" + creator +
            ", collaboratorsUrl='" + collaboratorsUrl + '\'' +
            ", projectsUrl='" + projectsUrl + '\'' +
            ", createdAt='" + createdAt + '\'' +
            ", updatedAt='" + updatedAt + '\'' +
            ", autoUpgrade=" + autoUpgrade +
            ", upgradeUrl='" + upgradeUrl + '\'' +
            ", canStartProTrial=" + canStartProTrial +
            ", proTrialEndsAt=" + proTrialEndsAt +
            ", proTrialFeature=" + proTrialFeature +
            ", billingEmails=" + billingEmails +
            '}';
    }
}
