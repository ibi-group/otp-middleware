package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.utils.bugsnag.BugsnagDispatcherImpl;
import spark.Request;
import spark.Response;

public class BugsnagController {
    final BugsnagDispatcher bugsnagDispatcher;

    public BugsnagController() {
        bugsnagDispatcher = new BugsnagDispatcherImpl();
    }

    public String getErrorSummary(Request request, Response response) {
        response.type("application/json");
        return JsonUtils.toJson(bugsnagDispatcher.getErrorSummary());
    }

}
