package org.opentripplanner.middleware.bugsnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.opentripplanner.middleware.bugsnag.response.Error;
import org.opentripplanner.middleware.bugsnag.response.Project;

/**
 * Represents a project error extracted from a Bugsnag webhook delivery. Information related to this content (and Bugsnag
 * webhooks) can be found here: https://docs.bugsnag.com/product/integrations/data-forwarding/webhook/.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BugsnagWebHookDelivery {
    /** The project which this event is associated with */
    @JsonProperty("project")
    public Project project;

    /** The error which this event is associated with */
    @JsonProperty("error")
    public Error error;
}
