package org.opentripplanner.middleware.controllers.api;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the UserController class.
 */
public class UserControllerTest extends OtpMiddlewareTest {

    /**
     * Verify the method for extracting a user from a {@link Auth0UserProfile}.
     */
    @Test
    public void testGetUserFromProfile() {
        Auth0UserProfile profile = Auth0UserProfile.createTestAdminUser();
        User dbUser = createUser(profile.user_id, profile.email);

        UserController controller = new UserController("/api/");
        UserController.UserFromProfileResult result = controller.getUserFromProfile(profile);

        assertNotNull(result.user);
        assertNull(result.message);
        assertEquals(profile.user_id, result.user.auth0UserId);

        // tidy up
        Persistence.users.removeById(dbUser.id);
    }

    /**
     * Same as above, but user with auth0UserId is not in Mongo.
     */
    @Test
    public void testGetUserFromProfile_noUser() {
        Auth0UserProfile profile = Auth0UserProfile.createTestAdminUser();
        User dbUser = createUser(profile.user_id + "-stuff", profile.email);

        UserController controller = new UserController("/api/");
        UserController.UserFromProfileResult result = controller.getUserFromProfile(profile);

        assertNull(result.user);
        assertNotNull(result.message);
        assertEquals(String.format(UserController.NO_USER_WITH_AUTH0_ID_MESSAGE, profile.user_id), result.message);

        // tidy up
        Persistence.users.removeById(dbUser.id);
    }

    /**
     * Same as above, but a profile is not given.
     */
    @Test
    public void testGetUserFromProfile_noProfile() {
        Auth0UserProfile profile = Auth0UserProfile.createTestAdminUser();
        User dbUser = createUser(profile.user_id, profile.email);

        UserController controller = new UserController("/api/");
        UserController.UserFromProfileResult result = controller.getUserFromProfile(null);

        assertNull(result.user);
        assertNotNull(result.message);
        assertEquals(UserController.NO_AUTH0_PROFILE_MESSAGE, result.message);

        // tidy up
        Persistence.users.removeById(dbUser.id);
    }

    /**
     * Utility to create user and store in database.
     */
    private User createUser(String auth0UserId, String email) {
        User user = new User();
        user.auth0UserId = auth0UserId;
        user.email = email;
        Persistence.users.create(user);
        return user;
    }
}
