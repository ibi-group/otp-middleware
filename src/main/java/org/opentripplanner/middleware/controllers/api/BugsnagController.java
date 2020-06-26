package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.bugsnag.EventSummary;
import org.opentripplanner.middleware.bugsnag.response.Project;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Responsible for providing the current set of Bugsnag events to the calling service
 */
public class BugsnagController {

    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;

    /**
     * Get all Bugsnag events from Mongo and replace the project id with the project name and return
     */
    public static String getEventSummary(Request request, Response response) {
        response.type("application/json");

        List<EventSummary> eventSummaries = new ArrayList<>();
        List<BugsnagEvent> events = bugsnagEvents.getAll();
        HashMap<String, Project> projects = BugsnagDispatcher.getProjects();

        // FIXME: Group by error/project type?
        for (BugsnagEvent event : events) {
            Project project = projects.get(event.projectId);
            eventSummaries.add(new EventSummary(project, event));
        }

        return JsonUtils.toJson(eventSummaries);
    }

}
