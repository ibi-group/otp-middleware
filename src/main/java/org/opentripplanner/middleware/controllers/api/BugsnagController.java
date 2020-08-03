package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.ApiEndpoint;
import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.bugsnag.EventSummary;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagProject;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;

/**
 * Responsible for providing the current set of Bugsnag events to the calling service
 */
public class BugsnagController implements Endpoint {

    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;
    private static TypedPersistence<BugsnagProject> bugsnagProjects = Persistence.bugsnagProjects;
    private static final Logger LOG = LoggerFactory.getLogger(LogController.class);

    private final String ROOT_ROUTE;
    private final Class clazz;

    public BugsnagController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "admin/bugsnag/eventsummary";
        this.clazz = BugsnagEvent.class;
    }

    /**
     * This method is called on each object deriving from Endpoint by {@link SparkSwagger}
     * to register endpoints and generate the swagger documentation skeleton.
     * Here, we just register the GET method under the provided API prefix path
     * to retrieve the bugsnag event summaries.
     * @param restApi The object to which to attach the documentation.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ApiEndpoint apiEndpoint = restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription(String.format("Bugsnag controller with type:%s", clazz)),
            (q, a) -> LOG.info("Received request for 'logs' Rest API")
        );
        apiEndpoint
            // Important: Unlike what the method name suggests,
            // withResponseAsCollection does not generate an array of the specified class,
            // although it generates the type for that class in the swagger output.
            .get(path(ROOT_ROUTE).withResponseAsCollection(clazz),
                BugsnagController::getEventSummary, JsonUtils::toJson)

            // Options response for CORS
            .options(path(""), (req, res) -> "");
    }

    /**
     * Get all Bugsnag events from Mongo and replace the project id with the project name and return
     */
    public static List<EventSummary> getEventSummary(Request request, Response response) {
        response.type("application/json");

        List<EventSummary> eventSummaries = new ArrayList<>();
        List<BugsnagEvent> events = bugsnagEvents.getAll();

        // FIXME: Group by error/project type?
        for (BugsnagEvent event : events) {
            Bson filter = Filters.eq("projectId", event.projectId);
            BugsnagProject project = bugsnagProjects.getOneFiltered(filter);
            eventSummaries.add(new EventSummary(project, event));
        }

        return eventSummaries;
    }
}
