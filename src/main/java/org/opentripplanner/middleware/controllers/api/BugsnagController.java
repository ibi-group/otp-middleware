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
import static org.opentripplanner.middleware.utils.HttpUtils.MIMETYPES_JSONONLY;

/**
 * Responsible for providing the current set of Bugsnag events to the calling service
 */
public class BugsnagController implements Endpoint {

    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;
    private static TypedPersistence<BugsnagProject> bugsnagProjects = Persistence.bugsnagProjects;
    private static final Logger LOG = LoggerFactory.getLogger(LogController.class);

    private final String ROOT_ROUTE;

    public BugsnagController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "admin/bugsnag/eventsummary";
    }

    /**
     * Register the API endpoint and GET resource to retrieve Bugsnag event summaries
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ApiEndpoint apiEndpoint = restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for reporting and retrieving application errors using Bugsnag."),
            (q, a) -> LOG.info("Received request for 'bugsnag' Rest API")
        );
        apiEndpoint
            .get(path(ROOT_ROUTE)
                    .withDescription("Gets a list of all Bugsnag event summaries.")
                    .withProduces(MIMETYPES_JSONONLY)
                    // Note: unlike the name suggests, withResponseAsCollection does not generate an array
                    // as the return type for this method. (It does generate the type for that class nonetheless.)
                    .withResponseAsCollection(BugsnagEvent.class),
                BugsnagController::getEventSummary, JsonUtils::toJson)

            // Options response for CORS
            .options(path(""), (req, res) -> "");
    }

    /**
     * Get all Bugsnag events from Mongo and replace the project id with the project name and return
     */
    public static List<EventSummary> getEventSummary(Request request, Response response) {
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
