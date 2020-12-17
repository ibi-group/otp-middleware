package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.createUser;

/**
 * Tests to verify that persistence in MongoDB collections are functioning properly. A number of
 * {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class PersistenceTest extends OtpMiddlewareTestEnvironment {
    private static final String TEST_EMAIL = "john.doe@example.com";
    private static final Logger LOG = LoggerFactory.getLogger(PersistenceTest.class);

    OtpUser user = null;

    @Test
    public void canCreateUser() {
        user = createUser(TEST_EMAIL);
        String id = user.id;
        String retrievedId = Persistence.otpUsers.getById(id).id;
        assertEquals(id, retrievedId, "Found User ID should equal inserted ID.");
    }

    @Test
    public void canUpdateUser() {
        user = createUser(TEST_EMAIL);
        String id = user.id;
        final String updatedEmail = "jane.doe@example.com";
        user.email = updatedEmail;
        Persistence.otpUsers.replace(id, user);
        String retrievedEmail = Persistence.otpUsers.getById(id).email;
        assertEquals(updatedEmail, retrievedEmail, "Found User email should equal updated email.");
    }

    @Test
    public void canDeleteUser() {
        OtpUser userToDelete = createUser(TEST_EMAIL);
        Persistence.otpUsers.removeById(userToDelete.id);
        OtpUser user = Persistence.otpUsers.getById(userToDelete.id);
        assertNull(user, "Deleted User should no longer exist in database (should return as null).");
    }

    @AfterEach
    public void remove() {
        if (user != null) {
            LOG.info("Deleting user {}", user.id);
            Persistence.otpUsers.removeById(user.id);
        }
    }

}
