package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.mgmt.jobs.Job;
import com.auth0.json.mgmt.users.User;
import com.beerboy.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public abstract class AbstractUserController<U extends AbstractUser> extends ApiController<U> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractUserController.class);
    static final String NO_USER_WITH_AUTH0_ID_MESSAGE = "No user with auth0UserID=%s found.";
    private static final String TOKEN_PATH = "/fromtoken";
    private static final String VERIFICATION_EMAIL_PATH = "/verification-email";

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
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponseType(persistence.clazz),
                this::getUserFromRequest, JsonUtils::toJson
            )
            // Resend verification email
            .get(path(ROOT_ROUTE + VERIFICATION_EMAIL_PATH)
                    .withDescription("Triggers a job to resend the Auth0 verification email.")
                    .withResponseType(Job.class),
                this::resendVerificationEmail, JsonUtils::toJson
            );

        // Add the regular CRUD methods after defining the /fromtoken route.
        super.buildEndpoint(modifiedEndpoint);
    }

    /**
     * Obtains the correct AbstractUser-derived object from the Auth0UserProfile object.
     * (Used in getUserForRequest.)
     */
    protected abstract U getUserProfile(RequestingUser profile);

    /**
     * HTTP endpoint to get the {@link U} entity, if it exists, from an {@link RequestingUser} attribute
     * available from a {@link Request} (this is the case for '/api/secure/' endpoints).
     */
    private U getUserFromRequest(Request req, Response res) {
        RequestingUser profile = Auth0Connection.getUserFromRequest(req);
        U user = getUserProfile(profile);

        // If the user object is null, it is most likely because it was not created yet,
        // for instance, for users who just created an Auth0 login and have an Auth0UserProfile
        // but have not completed the account setup form yet.
        // For those users, the user profile would be 404 not found (as opposed to 403 forbidden).
        if (user == null) {
            logMessageAndHalt(req,
                HttpStatus.NOT_FOUND_404,
                String.format(NO_USER_WITH_AUTH0_ID_MESSAGE, profile.auth0UserId),
                null);
        }
        return user;
    }


    private Job resendVerificationEmail(Request req, Response res) {
        RequestingUser profile = Auth0Connection.getUserFromRequest(req);
        return Auth0Users.resendVerificationEmail(profile.auth0UserId);
    }

    /**
     * Before creating/storing a user in MongoDB, create the user in Auth0 and update the {@link U#auth0UserId}
     * with the value from Auth0.
     */
    @Override
    U preCreateHook(U user, Request req) {
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        // TODO: If MOD UI is to be an ApiUser, we may want to do an additional check here to determine if this is a
        //  first-party API user (MOD UI) or third party.
        if (requestingUser.isThirdPartyUser() && user instanceof OtpUser) {
            // Do not create Auth0 account for OtpUsers created on behalf of third party API users.
            return user;
        } else {
            // For any other user account, create Auth0 account
            User auth0UserProfile = Auth0Users.createNewAuth0User(user, req, this.persistence);
            return Auth0Users.updateAuthFieldsForUser(user, auth0UserProfile);
        }
    }

    @Override
    U preUpdateHook(U user, U preExistingUser, Request req) {
        Auth0Users.validateExistingUser(user, preExistingUser, req, this.persistence);
        return user;
    }

    /**
     * Before deleting the user in MongoDB, attempt to delete the user in Auth0.
     */
    @Override
    boolean preDeleteHook(U user, Request req) {
        return true;
    }
}
