package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.bugsnag.ErrorSummary;
import org.opentripplanner.middleware.bugsnag.response.Project;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;

public class BugsnagController {

    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;

    public static String getErrorSummary(Request request, Response response) {
        response.type("application/json");

        List<ErrorSummary> errorSummaries = new ArrayList<>();
        List<BugsnagEvent> events = bugsnagEvents.getAll();
        for (BugsnagEvent event : events) {
            Project project = BugsnagDispatcher.PROJECTS.get(event.projectId);
            errorSummaries.add(new ErrorSummary(project, event));
        }

        return JsonUtils.toJson(errorSummaries);
    }

}
