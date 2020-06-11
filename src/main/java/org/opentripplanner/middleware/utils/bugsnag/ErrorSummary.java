package org.opentripplanner.middleware.utils.bugsnag;

import org.opentripplanner.middleware.utils.bugsnag.response.Project;
import org.opentripplanner.middleware.utils.bugsnag.response.ProjectError;

import java.util.Date;

public class ErrorSummary {
    public String errorId;
    public String errorClass;
    public String message;
    public int numOfEvents;
    public Date firstSeen;
    public Date lastSeen;
    public String projectId;
    public String projectName;

    public ErrorSummary(Project project, ProjectError projectError) {
        this.projectName = project.name;
        this.projectId = projectError.projectId;
        this.errorId = projectError.id;
        this.errorClass = projectError.errorClass;
        this.message = projectError.message;
        this.numOfEvents = projectError.events;
        this.firstSeen = projectError.firstSeen;
        this.lastSeen = projectError.lastSeen;
    }
}
