package org.opentripplanner.middleware.controllers.api;

import com.mongodb.client.model.Filters;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Request;

/**
 * Implementation of the {@link AbstractUserController} for {@link OtpUser}.
 */
public class OtpUserController extends AbstractUserController<OtpUser> {
    public static final String OTP_USER_PATH = "secure/user";
    public OtpUserController(String apiPrefix) {
        super(apiPrefix, Persistence.otpUsers, OTP_USER_PATH);
    }

    @Override
    OtpUser preCreateHook(OtpUser user, Request req) {
        // Check API key and assign user to appropriate third-party application. Note: this is only relevant for
        // instances of otp-middleware running behind API Gateway.
        String apiKey = req.headers("x-api-key");
        ApiUser apiUser = Persistence.apiUsers.getOneFiltered(Filters.eq("apiKeys.value", apiKey));
        if (apiUser != null) {
            // If API user found, assign to new OTP user.
            user.applicationId = apiUser.id;
        } else {
            // If API user not found, report to Bugsnag for further investigation.
            BugsnagReporter.reportErrorToBugsnag(
                "OTP user created with API key that is not linked to any API user",
                apiKey,
                new IllegalArgumentException("API key not linked to API user.")
            );
        }
        return super.preCreateHook(user, req);
    }

    @Override
    protected OtpUser getUserProfile(Auth0UserProfile profile) {
        return profile.otpUser;
    }
}
