package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Controller to get user object for current logged-in auth0 user.
 */
public class Auth0UserController implements Endpoint {
    private final String ROOT_ROUTE;
    private static final Logger LOG = LoggerFactory.getLogger(Auth0UserController.class);
    private final TypedPersistence<User> persistence;

    public Auth0UserController(String apiPrefix) {
        this.persistence = Persistence.users;
        this.ROOT_ROUTE = apiPrefix + "secure/auth0user";
    }

    /**
     * This method is called by {@link SparkSwagger} to register endpoints and generate the docs.
     * @param restApi The object to which to attach the documentation.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        LOG.info("Registering routes and enabling docs for auth0user route at {}", ROOT_ROUTE);

        restApi.endpoint(endpointPath(ROOT_ROUTE)
            .withDescription("Interface for querying user from auth0 logins."), (q, a) -> LOG.info("Received request for auth0user Rest API"))

            // Careful here!
            // If using lambdas with the GET method, a bug in spark-swagger
            // requires you to write path(<entire_route>).
            // If you use `new GsonRoute() {...}` with the GET method, you only need to write path(<relative_to_endpointPath>).
            // Other HTTP methods are not affected by this bug.

            // Default GET ).
            .get(path(ROOT_ROUTE)
                .withDescription("Retrieves a User entity (based on auth0UserId from request token).")
                .withResponseType(persistence.clazz),
                this::retrieve, JsonUtils::toJson
            )

            // Options response for CORS
            .options(path(""), (req, res) -> "");
    }

    /**
     * HTTP endpoint to get the User entity from auth0.
     */
    private User retrieve(Request req, Response res) {
        Auth0UserProfile profile = req.attribute("user");
        User result = null;
        String message = "Unknown error.";

        if (profile != null) {
            String auth0UserId = profile.user_id;
            result = persistence.getOneFiltered(eq("auth0UserId", auth0UserId));
            if (result == null) message = String.format("No user with auth0UserID=%s found.", auth0UserId);
        } else {
          message = "Auth0 profile could not be processed.";
        }

        if (result == null) {
            logMessageAndHalt(req, HttpStatus.NOT_FOUND_404, message,null);
        }
        return result;
    }
}
