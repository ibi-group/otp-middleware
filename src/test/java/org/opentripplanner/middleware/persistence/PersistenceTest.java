package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createMonitoredTrip;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.createUser;

/**
 * Tests to verify that persistence in MongoDB collections are functioning properly. A number of
 * {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class PersistenceTest extends OtpMiddlewareTest {
    private static final String TEST_EMAIL = "john.doe@example.com";

    @Test
    public void canCreateUser() {
        OtpUser user = createUser(TEST_EMAIL);
        String id = user.id;
        System.out.println("User id:" + id);
        String retrievedId = Persistence.otpUsers.getById(id).id;
        assertEquals(id, retrievedId, "Found User ID should equal inserted ID.");
        // tidy up
        Persistence.otpUsers.removeById(id);
    }

    @Test
    public void canUpdateUser() {
        OtpUser user = createUser(TEST_EMAIL);
        String id = user.id;
        final String updatedEmail = "jane.doe@example.com";
        user.email = updatedEmail;
        Persistence.otpUsers.replace(id, user);
        String retrievedEmail = Persistence.otpUsers.getById(id).email;
        assertEquals(updatedEmail, retrievedEmail, "Found User email should equal updated email.");
        // tidy up
        Persistence.otpUsers.removeById(id);
    }

    @Test
    public void canDeleteUser() {
        OtpUser userToDelete = createUser(TEST_EMAIL);
        Persistence.otpUsers.removeById(userToDelete.id);
        OtpUser user = Persistence.otpUsers.getById(userToDelete.id);
        assertNull(user, "Deleted User should no longer exist in database (should return as null).");
    }

    @Test
    public void canCreateMonitoredTrip() {
        String userId = "123456";
        MonitoredTrip monitoredTrip = createMonitoredTrip(userId);
        MonitoredTrip retrieved = Persistence.monitoredTrip.getById(monitoredTrip.id);
        assertEquals(monitoredTrip.id, retrieved.id, "Found monitored trip ID should equal inserted ID.");
        // tidy up
        Persistence.monitoredTrip.removeById(monitoredTrip.id);
    }

}
