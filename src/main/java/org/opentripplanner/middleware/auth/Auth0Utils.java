package org.opentripplanner.middleware.auth;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.User;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class Auth0Utils {

    /**
     * Check that the authenticated user matches the requesting user. This is to prevent unauthorized access to user
     * information.
     */
    public static void isAuthorizedUser(User user, Request request) {
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(request);

        if (!requestingUser.user_id.equalsIgnoreCase(user.auth0UserId)) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unauthorized access to service.");
        }
    }

}
