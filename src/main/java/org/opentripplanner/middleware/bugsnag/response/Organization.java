package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Organization {

    public String id;
    public String name;
    public String slug;
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
