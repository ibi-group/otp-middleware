package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.response.EventException;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.MonitoredComponent;

import java.util.Date;
import java.util.List;

/**
 * Event summary information provided to calling services. Response is based on static project and dynamic event
 * information. Additional information relating to this can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations/event-data-requests/create-an-event-data-request
 */
public class EventSummary {

    /** The error which this event is associated with */
    public String errorId;

    /** A list of {@link EventException) classes and messages associated with this event */
    public List<EventException> exceptions;

    /** The date/time Bugsnag received the event */
    public Date received;

    /** The project which this event is associated with */
    public String projectId = "-1";

    /** The associated project name */
    public String projectName = "Unknown";

    /** Associated environment e.g. Test, Dev, Production */
    public String releaseStage;

    public EventSummary(MonitoredComponent component, BugsnagEvent bugsnagEvent) {
        if (component != null) {
            this.projectName = component.name;
            this.projectId = component.bugsnagProjectId;
        }
        this.errorId = bugsnagEvent.id;
        this.exceptions = bugsnagEvent.exceptions;
        this.received = bugsnagEvent.receivedAt;
        this.releaseStage = bugsnagEvent.app.releaseStage;
    }
}
