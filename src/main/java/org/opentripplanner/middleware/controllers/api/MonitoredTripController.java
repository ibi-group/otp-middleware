package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.auth.Auth0Connection.isAuthorized;
import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

// Open API documentation for inherited CRUD methods
/**
 * @api [get] /api/secure/monitoredtrip
 * tags:
 * - "api/secure/monitoredtrip"
 * description: "Gets a list of all MonitoredTrip entities."
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
 *             $ref: "#/components/schemas/MonitoredTrip"
 *     headers:
 *       Access-Control-Allow-Origin:
 *         schema:
 *           type: "string"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip"
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
 * @api [options] /api/secure/monitoredtrip
 * responses:
 *   200:
 *     $ref: "#/components/responses/AllowCORS"
 * tags:
 *  - "api/secure/monitoredtrip"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip"
 *   responses:
 *     default:
 *       statusCode: 200
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Methods: "'GET,POST,OPTIONS'"
 *         method.response.header.Access-Control-Allow-Headers: "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'"
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *   passthroughBehavior: when_no_match
 *   httpMethod: OPTIONS
 *   type: http
 */

/**
 * @api [post] /api/secure/monitoredtrip
 * tags:
 * - "api/secure/monitoredtrip"
 * description: "Creates a new MonitoredTrip entity."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * requestBody:
 *   $ref: "#/components/requestBodies/MonitoredTrip"
 * responses:
 *   200:
 *     $ref: "#/components/responses/MonitoredTrip"
 *   400:
 *     description: "400 response"
 *     headers:
 *       Access-Control-Allow-Origin:
 *         schema:
 *           type: "string"
 *   500:
 *     description: "500 response"
 *     headers:
 *       Access-Control-Allow-Origin:
 *         schema:
 *           type: "string"
 *   401:
 *     description: "401 response"
 *     headers:
 *       Access-Control-Allow-Origin:
 *         schema:
 *           type: "string"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip"
 *   responses:
 *     200:
 *       statusCode: "200"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *     401:
 *       statusCode: "401"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *     5\d{2}:
 *       statusCode: "500"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *   requestParameters:
 *     integration.request.header.Authorization: "method.request.header.Authorization"
 *   passthroughBehavior: "when_no_match"
 *   httpMethod: "POST"
 *   type: "http"
 *
 */

// fromtoken route
/**
 * @api [get] /api/secure/monitoredtrip/fromtoken
 * tags:
 * - "api/secure/monitoredtrip"
 * description: "Retrieve a user from the Auth0 token."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * responses:
 *   200:
 *     $ref: "#/components/responses/MonitoredTrip"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip/fromtoken"
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
 * @api [options] /api/secure/monitoredtrip/fromtoken
 * responses:
 *   200:
 *     $ref: "#/components/responses/AllowCORS"
 * tags:
 *  - "api/secure/monitoredtrip"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip/fromtoken"
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

// {id} methods
/**
 * @api [get] /api/secure/monitoredtrip/{id}
 * tags:
 * - "api/secure/monitoredtrip"
 * description: "Returns an MonitoredTrip entity with the specified id, or 404 if not found."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * - $ref: '#/components/parameters/IDParameter'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * responses:
 *   200:
 *     $ref: "#/components/responses/MonitoredTrip"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip/{id}"
 *   responses:
 *     default:
 *       statusCode: "200"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *   requestParameters:
 *     integration.request.header.Authorization: "method.request.header.Authorization"
 *     integration.request.path.id: "method.request.path.id"
 *   passthroughBehavior: "when_no_match"
 *   httpMethod: "GET"
 *   type: "http"
 */
/**
 * @api [put] /api/secure/monitoredtrip/{id}
 * tags:
 * - "api/secure/monitoredtrip"
 * description: "Updates and returns the MonitoredTrip entity with the specified id, or\
 *   \ 404 if not found."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * - $ref: '#/components/parameters/IDParameter'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * requestBody:
 *   $ref: "#/components/requestBodies/MonitoredTrip"
 * responses:
 *   200:
 *     $ref: "#/components/responses/MonitoredTrip"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip/{id}"
 *   responses:
 *     default:
 *       statusCode: "200"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *   requestParameters:
 *     integration.request.header.Authorization: "method.request.header.Authorization"
 *     integration.request.path.id: "method.request.path.id"
 *   passthroughBehavior: "when_no_match"
 *   httpMethod: "PUT"
 *   type: "http"
 */
/**
 * @api [delete] /api/secure/monitoredtrip/{id}
 * tags:
 * - "api/secure/monitoredtrip"
 * description: "Deletes the MonitoredTrip entity with the specified id if it exists."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * - $ref: '#/components/parameters/IDParameter'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * responses:
 *   200:
 *     description: "successful operation"
 *     headers:
 *       Access-Control-Allow-Origin:
 *         schema:
 *           type: "string"
 *   400:
 *     description: "400 response"
 *   401:
 *     description: "401 response"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip/{id}"
 *   responses:
 *     200:
 *       statusCode: "200"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *     400:
 *       statusCode: "400"
 *     401:
 *       statusCode: "401"
 *   requestParameters:
 *     integration.request.header.Authorization: "method.request.header.Authorization"
 *     integration.request.path.id: "method.request.path.id"
 *   passthroughBehavior: "when_no_match"
 *   httpMethod: "DELETE"
 *   type: "http"
 */
/**
 * @api [options] /api/secure/monitoredtrip/{id}
 * tags:
 * - "api/secure/monitoredtrip"
 * parameters:
 * - $ref: '#/components/parameters/IDParameter'
 * responses:
 *   200:
 *     $ref: "#/components/responses/AllowCORS"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/monitoredtrip/{id}"
 *   responses:
 *     default:
 *       statusCode: "200"
 *       responseParameters:
 *         method.response.header.Access-Control-Allow-Methods: "'DELETE,GET,OPTIONS,PUT'"
 *         method.response.header.Access-Control-Allow-Headers: "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'"
 *         method.response.header.Access-Control-Allow-Origin: "'*'"
 *   requestParameters:
 *     integration.request.path.id: "method.request.path.id"
 *   passthroughBehavior: "when_no_match"
 *   httpMethod: "OPTIONS"
 *   type: "http"
 *
 */


/**
 * Implementation of the {@link ApiController} abstract class for managing monitored trips. This controller connects
 * with Auth0 services using the hooks provided by {@link ApiController}.
 */
public class MonitoredTripController extends ApiController<MonitoredTrip> {
    private static final int MAXIMUM_PERMITTED_MONITORED_TRIPS
        = getConfigPropertyAsInt("MAXIMUM_PERMITTED_MONITORED_TRIPS", 5);

    public MonitoredTripController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredTrips, "secure/monitoredtrip");
    }

    @Override
    MonitoredTrip preCreateHook(MonitoredTrip monitoredTrip, Request req) {
        isAuthorized(monitoredTrip.userId, req);
        verifyBelowMaxNumTrips(monitoredTrip.userId, req);

        return monitoredTrip;
    }

    @Override
    MonitoredTrip preUpdateHook(MonitoredTrip monitoredTrip, MonitoredTrip preExisting, Request req) {
        isAuthorized(monitoredTrip.userId, req);
        return monitoredTrip;
    }

    @Override
    boolean preDeleteHook(MonitoredTrip monitoredTrip, Request req) {
        // Authorization checks are done prior to this hook
        return true;
    }

    /**
     * Confirm that the maximum number of saved monitored trips has not been reached
     */
    private void verifyBelowMaxNumTrips(String userId, Request request) {

        // filter monitored trip on user id to find out how many have already been saved
        Bson filter = Filters.and(eq("userId", userId));
        long count = this.persistence.getCountFiltered(filter);
        if (count >= MAXIMUM_PERMITTED_MONITORED_TRIPS) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Maximum permitted saved monitored trips reached. Maximum = " + MAXIMUM_PERMITTED_MONITORED_TRIPS);
        }
    }
}
