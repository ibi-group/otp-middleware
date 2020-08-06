package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.beerboy.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.auth.Auth0Users.createNewAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.deleteAuth0User;
import static org.opentripplanner.middleware.auth.Auth0Users.updateAuthFieldsForUser;
import static org.opentripplanner.middleware.auth.Auth0Users.validateExistingUser;
import static org.opentripplanner.middleware.utils.HttpUtils.MIMETYPES_JSONONLY;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public abstract class AbstractUserController<U extends AbstractUser> extends ApiController<U> {
    static final String NO_USER_WITH_AUTH0_ID_MESSAGE = "No user with auth0UserID=%s found.";
    private static final String TOKEN_PATH = "/fromtoken";

    /**
     * Constructor that child classes can access to setup persistence and API route.
     */
    public AbstractUserController(String apiPrefix, TypedPersistence<U> persistence, String resource) {
        super(apiPrefix, persistence, resource);
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
                    .withDescription("Retrieves an " + persistence.clazz.getSimpleName() + " entity using an Auth0 access token passed in an Authorization header.")
                    .withProduces(MIMETYPES_JSONONLY)
                    .withResponseType(persistence.clazz),
                this::getUserFromRequest, JsonUtils::toJson
            )

            // Options response for CORS for the token path
            .options(path(TOKEN_PATH), (req, res) -> "");

        // Add the regular CRUD methods after defining the /fromtoken route.
        super.buildEndpoint(modifiedEndpoint);
    }

    /**
     * Obtains the correct AbstractUser-derived object from the Auth0UserProfile object.
     * (Used in getUserForRequest.)
     */
    protected abstract U getUserProfile(Auth0UserProfile profile);

    /**
     * HTTP endpoint to get the {@link U} entity, if it exists, from an {@link Auth0UserProfile} attribute
     * available from a {@link Request} (this is the case for '/api/secure/' endpoints).
     */
    private U getUserFromRequest(Request req, Response res) {
        Auth0UserProfile profile = Auth0Connection.getUserFromRequest(req);
        U user = getUserProfile(profile);

        // If the user object is null, it is most likely because it was not created yet,
        // for instance, for users who just created an Auth0 login and have an Auth0UserProfile
        // but have not completed the account setup form yet.
        // For those users, the user profile would be 404 not found (as opposed to 403 forbidden).
        if (user == null) {
            logMessageAndHalt(req, HttpStatus.NOT_FOUND_404, String.format(NO_USER_WITH_AUTH0_ID_MESSAGE, profile.auth0UserId), null);
        }
        return user;
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link U#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    U preCreateHook(U user, Request req) {
        com.auth0.json.mgmt.users.User auth0UserProfile = createNewAuth0User(user, req, this.persistence);
        return updateAuthFieldsForUser(user, auth0UserProfile);
    }

    @Override
    U preUpdateHook(U user, U preExistingUser, Request req) {
        validateExistingUser(user, preExistingUser, req, this.persistence);
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(U user, Request req) {
        try {
            deleteAuth0User(user.auth0UserId);
        } catch (Auth0Exception e) {
            // FIXME: Add Bugsnag error report.
            logMessageAndHalt(req, 500, "Error deleting user.", e);
            return false;
        }
        return true;
    }
}
