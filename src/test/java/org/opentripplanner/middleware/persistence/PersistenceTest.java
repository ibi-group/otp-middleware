package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests to verify that persistence in MongoDB collections are functioning properly. A number of
 * {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class PersistenceTest extends OtpMiddlewareTest {
    private static final String TEST_EMAIL = "john.doe@example.com";

    @Test
    public void canCreateUser() {
        User user = createUser(TEST_EMAIL);
        String id = user.id;
        String retrievedId = Persistence.users.getById(id).id;
        assertEquals(id, retrievedId, "Found User ID should equal inserted ID.");
    }

    @Test
    public void canUpdateUser() {
        User user = createUser(TEST_EMAIL);
        String id = user.id;
        final String updatedEmail = "jane.doe@example.com";
        user.email = updatedEmail;
        Persistence.users.replace(id, user);
        String retrievedEmail = Persistence.users.getById(id).email;
        assertEquals(updatedEmail, retrievedEmail, "Found User email should equal updated email.");
    }

    @Test
    public void canDeleteUser() {
        User userToDelete = createUser(TEST_EMAIL);
        Persistence.users.removeById(userToDelete.id);
        User user = Persistence.users.getById(userToDelete.id);
        assertNull(user, "Deleted User should no longer exist in database (should return as null).");
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

    @Test
    public void canCreateTripRequest() {
        TripRequest tripRequest = createTripRequest();
        String id = tripRequest.id;
        String retrievedId = Persistence.tripRequest.getById(id).id;
        assertEquals(id, retrievedId, "Found Trip request ID should equal inserted ID.");
    }

    @Test
    public void canDeleteTripRequest() {
        TripRequest tripRequestToDelete = createTripRequest();
        Persistence.tripRequest.removeById(tripRequestToDelete.id);
        TripRequest tripRequest = Persistence.tripRequest.getById(tripRequestToDelete.id);
        assertNull(tripRequest, "Deleted TripRequest should no longer exist in database (should return as null).");
    }

    private TripRequest createTripRequest() {
        String userId = "123456";
        String batchId = "783726";
        String fromPlace = "28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034";
        String toPlace = "28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759";
        String queryParams = "arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";
        TripRequest tripRequest = new TripRequest(userId, batchId, fromPlace, toPlace, queryParams);
        Persistence.tripRequest.create(tripRequest);
        return tripRequest;
    }


}
