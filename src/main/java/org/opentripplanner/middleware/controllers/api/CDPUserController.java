package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.CDPUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.HaltException;
import spark.Request;

/**
 * Implementation of the {@link AbstractUserController} for {@link CDPUser}. This controller also contains methods for
 * managing an {@link CDPUser}'s S3 keys.
 */
public class CDPUserController extends AbstractUserController<CDPUser> {
    public static final String CDP_USER_PATH = "secure/cdp";

    /**
     * Constructor that child classes can access to setup persistence and API route.
     */
    public CDPUserController(String apiPrefix) {
        super(apiPrefix, Persistence.cdpUsers, CDP_USER_PATH);
    }

    /**
     * Obtains the correct CDPUser from the Auth0UserProfile object.
     * (Used in getUserForRequest.)
     *
     * @param profile
     */
    @Override
    protected CDPUser getUserProfile(RequestingUser profile) {
        return profile.cdpUser;
    }

    /**
     * Hook called before CDPUser is created in MongoDB.
     *
     * @param user
     * @param req
     */
    @Override
    CDPUser preCreateHook(CDPUser user, Request req) {
        // Call AbstractUserController#preCreateHook and delete api key in case something goes wrong.
        try {
            return super.preCreateHook(user, req);
        } catch (HaltException e) {
            user.delete();
            throw e;
        }
    }

    /**
     * Method to run before a CDPUser is deleted
     */
    @Override
    boolean preDeleteHook(CDPUser user, Request req) {
        return true;
    }
}
