package org.opentripplanner.middleware.persistence;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.User;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests to verify that persistence in MongoDB collections are functioning properly. A number of
 * {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class PersistenceTest extends OtpMiddlewareTest {
    private static final String TEST_EMAIL = "john.doe@example.com";
    /**
     * Prepare and start a testing-specific web server
     */
    @BeforeClass
    public static void setUp() throws IOException {
        // Start server if it isn't already running.
        OtpMiddlewareTest.setUp();
    }

    @Test
    public void canCreateUser() {
        User user = createUser(TEST_EMAIL);
        String id = user.id;
        String retrievedId = Persistence.users.getById(id).id;
        assertEquals("Found User ID should equal inserted ID.", id, retrievedId);
    }

    @Test
    public void canUpdateUser() {
        User user = createUser(TEST_EMAIL);
        String id = user.id;
        final String updatedEmail = "jane.doe@example.com";
        user.email = updatedEmail;
        Persistence.users.replace(id, user);
        String retrievedEmail = Persistence.users.getById(id).email;
        assertEquals("Found User email should equal updated email.", updatedEmail, retrievedEmail);
    }

    @Test
    public void canDeleteUser() {
        User userToDelete = createUser(TEST_EMAIL);
        Persistence.users.removeById(userToDelete.id);
        User user = Persistence.users.getById(userToDelete.id);
        assertNull("Deleted User should no longer exist in database (should return as null).", user);
    }

    /**
     * Utility to create user and store in database.
     */
    private User createUser(String email) {
        User user = new User();
        user.email = email;
        Persistence.users.create(user);
        return user;
    }
}
