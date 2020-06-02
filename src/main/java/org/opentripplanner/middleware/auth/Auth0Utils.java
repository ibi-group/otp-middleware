package org.opentripplanner.middleware.auth;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.User;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class Auth0Utils {

    /**
     * Confirm that the user exists
     */
    public static void isValidUser(Request request) {

        // assumption is that profile will never be null
        Auth0UserProfile profile = request.attribute("user");
        if (profile.otpUser == null) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unknown user.");
        }
    }

    /**
     * Confirm that the user's actions are on their items.
     */
    public static void isAuthorized(String userId, Request request) {

        if (userId == null) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unauthorized access.");
        }

        // assumption is that profile will never be null
        Auth0UserProfile profile = request.attribute("user");
        if (!profile.otpUser.id.equalsIgnoreCase(userId)) {
            logMessageAndHalt(request, HttpStatus.FORBIDDEN_403, "Unauthorized access.");
        }
    }
}
