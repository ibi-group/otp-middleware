package org.opentripplanner.middleware.bugsnag;

import org.opentripplanner.middleware.bugsnag.response.EventException;
import org.opentripplanner.middleware.bugsnag.response.Project;
import org.opentripplanner.middleware.models.BugsnagEvent;

import java.util.Date;
import java.util.List;

public class ErrorSummary {
    public String errorId;
    public List<EventException> exceptions;
    public Date received;
    public String projectId;
    public String projectName;
    public String releaseStage;

    public ErrorSummary(Project project, BugsnagEvent bugsnagEvent) {
        this.projectName = project.name;
        this.projectId = project.id;
        this.errorId = bugsnagEvent.id;
        this.exceptions = bugsnagEvent.exceptions;
        this.received = bugsnagEvent.receivedAt;
        this.releaseStage = bugsnagEvent.app.releaseStage;
    }
}
