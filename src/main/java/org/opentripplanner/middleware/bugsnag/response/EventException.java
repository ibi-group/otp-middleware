package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This class is referenced within {@link org.opentripplanner.middleware.models.BugsnagEvent} and is part of an event
 * data request. Information relating to this can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations/collaborators/create-an-event-data-request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventException {

    /** An exception error class associated with an event */
    public String errorClass;

    /** An exception message associated with an event */
    public String message;
}
