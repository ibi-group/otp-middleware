package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import com.google.common.collect.Maps;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Sorts;
import org.opentripplanner.middleware.bugsnag.EventSummary;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagProject;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.controllers.api.ApiController.DEFAULT_LIMIT;
import static org.opentripplanner.middleware.controllers.api.ApiController.LIMIT;
import static org.opentripplanner.middleware.controllers.api.ApiController.LIMIT_PARAM;
import static org.opentripplanner.middleware.controllers.api.ApiController.OFFSET;
import static org.opentripplanner.middleware.controllers.api.ApiController.OFFSET_PARAM;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;

/**
 * Responsible for providing the current set of Bugsnag events to the calling service
 */
public class BugsnagController implements Endpoint {

    private static final TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;
    private static final TypedPersistence<BugsnagProject> bugsnagProjects = Persistence.bugsnagProjects;

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
        restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for reporting and retrieving application errors using Bugsnag."),
            HttpUtils.NO_FILTER
        ).get(
            path(ROOT_ROUTE)
                .withDescription("Gets a paginated list of the latest Bugsnag event summaries.")
                .withQueryParam(LIMIT)
                .withQueryParam(OFFSET)
                .withProduces(JSON_ONLY)
                // Note: unlike what the name suggests, withResponseAsCollection does not generate an array
                // as the return type for this method. (It does generate the type for that class nonetheless.)
                .withResponseAsCollection(BugsnagEvent.class),
            BugsnagController::getEventSummaries, JsonUtils::toJson);
    }

    /**
     * Get the latest Bugsnag {@link EventSummary} from MongoDB (event summary is composed of {@link BugsnagEvent} and
     * {@link BugsnagProject}.
     */
    private static ResponseList<EventSummary> getEventSummaries(Request req, Response res) {
        int limit = HttpUtils.getQueryParamFromRequest(req, LIMIT_PARAM, 0, DEFAULT_LIMIT, 100);
        int offset = HttpUtils.getQueryParamFromRequest(req, OFFSET_PARAM, 0, 0);
        // Get latest events from database.
        FindIterable<BugsnagEvent> events = bugsnagEvents.getSortedIterableWithOffsetAndLimit(
            Sorts.descending("receivedAt"),
            offset,
            limit
        );
        // Get Bugsnag projects by id (avoid multiple queries to Mongo for the same project).
        Map<String, BugsnagProject> projectsById = Maps.uniqueIndex(bugsnagProjects.getAll(), p -> p.projectId);
        // Construct event summaries from project map.
        // FIXME: Group by error/project type?
        List<EventSummary> eventSummaries = events
            .map(event -> new EventSummary(projectsById.get(event.projectId), event))
            .into(new ArrayList<>());
        return new ResponseList<>(EventSummary.class, eventSummaries, offset, limit, bugsnagEvents.getCount());
    }
}
