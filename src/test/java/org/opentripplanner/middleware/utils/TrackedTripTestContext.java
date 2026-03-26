package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.auth.Auth0Users;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;

import java.util.Map;

import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;

/**
 * Holds ambient test data such as OTP users and related data.
 */
public class TrackedTripTestContext {
    public final OtpUser otpUser;
    public final Map<String, String> headers;

    public TrackedTripTestContext() throws Exception {
        otpUser = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("test-solootpuser"));

        // Should use Auth0User.createNewAuth0User but this generates a random password preventing the mock headers
        // from being able to use TEMP_AUTH0_USER_PASSWORD.
        var auth0User = Auth0Users.createAuth0UserForEmail(otpUser.email, TEMP_AUTH0_USER_PASSWORD);
        otpUser.auth0UserId = auth0User.getId();
        Persistence.otpUsers.replace(otpUser.id, otpUser);
        headers = ApiTestUtils.getMockHeaders(otpUser);
    }

    public void tearDown() {
        OtpUser fetchedUser = Persistence.otpUsers.getById(otpUser.id);
        if (fetchedUser != null) fetchedUser.delete(true);
    }
}
