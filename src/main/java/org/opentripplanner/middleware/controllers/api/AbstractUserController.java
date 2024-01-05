package org.opentripplanner.middleware.controllers.api;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.jobs.Job;
import com.auth0.json.mgmt.users.User;
import io.github.manusant.ss.ApiEndpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager;
import org.opentripplanner.middleware.connecteddataplatform.TripHistoryUploadJob;
import org.opentripplanner.middleware.models.AbstractUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.auth.Auth0Users.deleteAuth0User;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link ApiController} abstract class for managing users. This controller connects with Auth0
 * services using the hooks provided by {@link ApiController}.
 */
public abstract class AbstractUserController<U extends AbstractUser> extends ApiController<U> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractUserController.class);
    static final String NO_USER_WITH_AUTH0_ID_MESSAGE = "No user with auth0UserID=%s found.";
    private static final String TOKEN_PATH = "/fromtoken";
    public static final String VERIFICATION_EMAIL_PATH = "/verification-email";

    /**
     * Constructor that child classes can access to setup persistence and API route.
     */
    public AbstractUserController(String apiPrefix, TypedPersistence<U> persistence, String resource) {
        super(apiPrefix, persistence, resource);
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        final String fullTokenPath = ROOT_ROUTE + TOKEN_PATH;
        LOG.info("Registering path {}.", fullTokenPath);

        // Add the user token route BEFORE the regular CRUD methods
        // (otherwise, /fromtoken requests would be considered
        // by spark as 'GET user with id "fromtoken"', which we don't want).
        ApiEndpoint modifiedEndpoint = baseEndpoint
            // Get user from token.
            .get(path(fullTokenPath)
                    .withDescription("Retrieves an " + persistence.clazz.getSimpleName() + " entity using an Auth0 access token passed in an Authorization header.")
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponses(stdResponses),
                this::getUserFromRequest, JsonUtils::toJson
            )
            .delete(path(fullTokenPath)
                    .withDescription("Deletes an " + persistence.clazz.getSimpleName() + " entity using an Auth0 access token passed in an Authorization header.")
                    .withResponses(stdResponses),
                this::deleteUserFromRequest, JsonUtils::toJson
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
        if (profile == null) {
            logMessageAndHalt(req,
                    HttpStatus.NOT_FOUND_404,
                    String.format(NO_USER_WITH_AUTH0_ID_MESSAGE, null),
                    null);
        }
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

    /**
     * HTTP endpoint to delete the {@link U} entity, if it exists, from an {@link RequestingUser} token
     * available from a {@link Request} (this is the case for '/api/secure/' endpoints).
     */
    private boolean deleteUserFromRequest(Request req, Response res) {
        RequestingUser profile = Auth0Connection.getUserFromRequest(req);
        if (profile == null) {
            logMessageAndHalt(req,
                HttpStatus.NOT_FOUND_404,
                String.format(NO_USER_WITH_AUTH0_ID_MESSAGE, null),
                null);
            return false;
        }
        U user = getUserProfile(profile);

        if (user != null) {
            // If a user record was found in Mongo, cascade delete, including its Auth0 ID.
            boolean result = user.delete();
            if (!result) {
                logMessageAndHalt(req,
                    HttpStatus.INTERNAL_SERVER_ERROR_500,
                    String.format("An error occurred deleting user with id '%s'.", user.id),
                    null);
            }
            return result;
        } else {
            // If no user record was found in Mongo, directly delete its Auth0 ID.
            try {
                deleteAuth0User(profile.auth0UserId);
                return true;
            } catch (Auth0Exception e) {
                logMessageAndHalt(req,
                    HttpStatus.INTERNAL_SERVER_ERROR_500,
                    String.format("Could not delete Auth0 user %s.", profile.auth0UserId),
                    e);
                return false;
            }
        }
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
        if (requestingUser.isAPIUser() && user instanceof OtpUser) {
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
        if (user instanceof OtpUser) {
            OtpUser otpUser = (OtpUser) user;
            OtpUser existingOtpUser = (OtpUser) preExistingUser;
            if (!otpUser.storeTripHistory && existingOtpUser.storeTripHistory) {
                // If an OTP user no longer wants their trip history stored, remove all history from MongoDB.
                ConnectedDataManager.removeUsersTripHistory(otpUser.id);
                // Kick-off a trip history upload job to recompile and upload trip data to S3 minus the user's trip
                // history.
                TripHistoryUploadJob tripHistoryUploadJob = new TripHistoryUploadJob();
                tripHistoryUploadJob.run();
            }

            // Include select attributes from existingOtpUser marked @JsonIgnore and
            // that are not set in otpUser, and other attributes that should not be modifiable
            // using web requests.
            otpUser.smsConsentDate = existingOtpUser.smsConsentDate;
            otpUser.email = existingOtpUser.email;
            otpUser.auth0UserId = existingOtpUser.auth0UserId;
            otpUser.isDataToolsUser = existingOtpUser.isDataToolsUser;
            otpUser.pushDevices = existingOtpUser.pushDevices;

        }
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
