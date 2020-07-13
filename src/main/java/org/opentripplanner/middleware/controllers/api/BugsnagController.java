package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.bugsnag.EventSummary;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagProject;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;
import spark.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for providing the current set of Bugsnag events to the calling service
 */
public class BugsnagController {

    private static TypedPersistence<BugsnagEvent> bugsnagEvents = Persistence.bugsnagEvents;
    private static TypedPersistence<BugsnagProject> bugsnagProjects = Persistence.bugsnagProjects;
     
    /*
     * Register http endpoints with {@link spark.Spark} instance at the provided API prefix.
     */
    public static void register (Service spark, String apiPrefix) {
        /**
         * @api [get] /api/admin/bugsnag/eventsummary
         * tags:
         * - "api/admin/bugsnag/eventsummary"
         * description: "Gets a list of recent Bugsnag events."
         * parameters:
         * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
         * security:
         * - ApiKey: [] # Required for AWS integration.
         *   Auth0Bearer: []
         * responses:
         *   200:
         *     description: "successful operation"
         *     content:
         *       application/json:
         *         schema:
         *           type: array
         *           items:
         *             $ref: "#/components/schemas/EventSummary"
         *     headers:
         *       Access-Control-Allow-Origin:
         *         schema:
         *           type: "string"
         * x-amazon-apigateway-integration:
         *   uri: "http://54.147.40.127/api/admin/bugsnag/eventsummary"
         *   responses:
         *     default:
         *       statusCode: "200"
         *       responseParameters:
         *         method.response.header.Access-Control-Allow-Origin: "'*'"
         *   requestParameters:
         *     integration.request.header.Authorization: "method.request.header.Authorization"
         *   passthroughBehavior: "when_no_match"
         *   httpMethod: "GET"
         *   type: "http"
         *
         */
        /**
         * @api [options] /api/admin/bugsnag/eventsummary
         * responses:
         *   200:
         *     $ref: "#/components/responses/AllowCORS"
         * tags:
         *  - "api/admin/bugsnag/eventsummary"
         * x-amazon-apigateway-integration:
         *   uri: "http://54.147.40.127/api/admin/bugsnag/eventsummary"
         *   responses:
         *     default:
         *       statusCode: 200
         *       responseParameters:
         *         method.response.header.Access-Control-Allow-Methods: "'GET,OPTIONS'"
         *         method.response.header.Access-Control-Allow-Headers: "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'"
         *         method.response.header.Access-Control-Allow-Origin: "'*'"
         *   passthroughBehavior: when_no_match
         *   httpMethod: OPTIONS
         *   type: http
         */

        // available at http://localhost:4567/api/admin/bugsnag/eventsummary
        spark.get(apiPrefix + "admin/bugsnag/eventsummary", BugsnagController::getEventSummary);
    }

    /**
     * Get all Bugsnag events from Mongo and replace the project id with the project name and return
     */
    public static String getEventSummary(Request request, Response response) {
        response.type("application/json");

        List<EventSummary> eventSummaries = new ArrayList<>();
        List<BugsnagEvent> events = bugsnagEvents.getAll();

        // FIXME: Group by error/project type?
        for (BugsnagEvent event : events) {
            Bson filter = Filters.eq("projectId", event.projectId);
            BugsnagProject project = bugsnagProjects.getOneFiltered(filter);
            eventSummaries.add(new EventSummary(project, event));
        }

        return JsonUtils.toJson(eventSummaries);
    }

}
