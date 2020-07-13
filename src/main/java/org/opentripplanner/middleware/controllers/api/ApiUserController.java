package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.beerboy.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.auth.Auth0Users.createNewAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.deleteAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.updateAuthFieldsForUser;
import static org.opentripplanner.middleware.auth.Auth0Users.validateExistingUser;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

// Open API documentation for inherited CRUD methods
/**
 * @api [get] /api/secure/application
 * tags:
 * - "api/secure/application"
 * description: "Gets a list of all ApiUser entities."
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
 *             $ref: "#/components/schemas/ApiUser"
 *     headers:
 *       Access-Control-Allow-Origin:
 *         schema:
 *           type: "string"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application"
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
 * @api [options] /api/secure/application
 * responses:
 *   200:
 *     $ref: "#/components/responses/AllowCORS"
 * tags:
 *  - "api/secure/application"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application"
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
 * @api [post] /api/secure/application
 * tags:
 * - "api/secure/application"
 * description: "Creates a new ApiUser entity."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * requestBody:
 *   $ref: "#/components/requestBodies/ApiUser"
 * responses:
 *   200:
 *     $ref: "#/components/responses/ApiUser"
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
 *   uri: "http://54.147.40.127/api/secure/application"
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
 * @api [get] /api/secure/application/fromtoken
 * tags:
 * - "api/secure/application"
 * description: "Retrieve a user from the Auth0 token."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * responses:
 *   200:
 *     $ref: "#/components/responses/ApiUser"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application/fromtoken"
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
 * @api [options] /api/secure/application/fromtoken
 * responses:
 *   200:
 *     $ref: "#/components/responses/AllowCORS"
 * tags:
 *  - "api/secure/application"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application/fromtoken"
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
 * @api [get] /api/secure/application/{id}
 * tags:
 * - "api/secure/application"
 * description: "Returns an ApiUser entity with the specified id, or 404 if not found."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * - $ref: '#/components/parameters/IDParameter'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * responses:
 *   200:
 *     $ref: "#/components/responses/ApiUser"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application/{id}"
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
 * @api [put] /api/secure/application/{id}
 * tags:
 * - "api/secure/application"
 * description: "Updates and returns the ApiUser entity with the specified id, or\
 *   \ 404 if not found."
 * parameters:
 * - $ref: '#/components/parameters/AWSAuthHeaderRequired'
 * - $ref: '#/components/parameters/IDParameter'
 * security:
 * - ApiKey: [] # Required for AWS integration.
 *   Auth0Bearer: []
 * requestBody:
 *   $ref: "#/components/requestBodies/ApiUser"
 * responses:
 *   200:
 *     $ref: "#/components/responses/ApiUser"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application/{id}"
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
 * @api [delete] /api/secure/application/{id}
 * tags:
 * - "api/secure/application"
 * description: "Deletes the ApiUser entity with the specified id if it exists."
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
 *   uri: "http://54.147.40.127/api/secure/application/{id}"
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
 * @api [options] /api/secure/application/{id}
 * tags:
 * - "api/secure/application"
 * parameters:
 * - $ref: '#/components/parameters/IDParameter'
 * responses:
 *   200:
 *     $ref: "#/components/responses/AllowCORS"
 * x-amazon-apigateway-integration:
 *   uri: "http://54.147.40.127/api/secure/application/{id}"
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
 * Implementation of the {@link ApiController} abstract class for managing API users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public class ApiUserController extends ApiController<ApiUser> {
    private static final Logger LOG = LoggerFactory.getLogger(ApiUserController.class);
    static final String NO_USER_WITH_AUTH0_ID_MESSAGE = "No user with auth0UserID=%s found.";
    private static final String TOKEN_PATH = "/fromtoken";

    public ApiUserController(String apiPrefix){
        super(apiPrefix, Persistence.apiUsers, "secure/application");
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        LOG.info("Registering path {}.", ROOT_ROUTE + TOKEN_PATH);

        // Add the user token route BEFORE the regular CRUD methods
        // (otherwise, /fromtoken requests would be considered
        // by spark as 'GET user with id "fromtoken"', which we don't want).
        ApiEndpoint modifiedEndpoint = baseEndpoint
            // Get user from token.
            .get(path(ROOT_ROUTE + TOKEN_PATH)
                    .withDescription("Retrieves an ApiUser entity using an Auth0 access token passed in an Authorization header.")
                    .withResponseType(persistence.clazz),
                this::getUserFromRequest, JsonUtils::toJson
            )

            // Options response for CORS for the token path
            .options(path(TOKEN_PATH), (req, res) -> "");

        // Add the regular CRUD methods after defining the /fromtoken route.
        super.buildEndpoint(modifiedEndpoint);
    }

    /**
     * HTTP endpoint to get the {@link ApiUser} entity, if it exists, from an {@link Auth0UserProfile} attribute
     * available from a {@link Request} (this is the case for '/api/secure/' endpoints).
     */
    private ApiUser getUserFromRequest(Request req, Response res) {
        Auth0UserProfile profile = Auth0Connection.getUserFromRequest(req);
        ApiUser user = profile.apiUser;

        // If the ApiUser object is null, it is most likely because it was not created yet,
        // for instance, for users who just created an Auth0 login and have an Auth0UserProfile
        // but have not completed the account setup form yet.
        // For those users, the ApiUser profile would be 404 not found (as opposed to 403 forbidden).
        if (user == null) {
            logMessageAndHalt(req, HttpStatus.NOT_FOUND_404, String.format(NO_USER_WITH_AUTH0_ID_MESSAGE, profile.auth0UserId), null);
        }
        return user;
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link ApiUser#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    ApiUser preCreateHook(ApiUser user, Request req) {
        User auth0UserProfile = createNewAuth0User(user, req, this.persistence);
        return updateAuthFieldsForUser(user, auth0UserProfile);
    }

    @Override
    ApiUser preUpdateHook(ApiUser user, ApiUser preExistingUser, Request req) {
        validateExistingUser(user, preExistingUser, req, this.persistence);
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(ApiUser user, Request req) {
        try {
            deleteAuth0User(user.auth0UserId);
        } catch (Auth0Exception e) {
            logMessageAndHalt(req, 500, "Error deleting user.", e);
            return false;
        }
        return true;
    }
}
