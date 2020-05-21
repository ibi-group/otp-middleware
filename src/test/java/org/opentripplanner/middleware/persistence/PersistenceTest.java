package org.opentripplanner.middleware.persistence;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.models.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.*;

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
        System.out.println("User id:" + id);
        String retrievedId = Persistence.users.getById(id).id;
        assertEquals(id, retrievedId, "Found User ID should equal inserted ID.");
        // tidy up
        Persistence.users.removeById(id);
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
        // tidy up
        Persistence.users.removeById(id);
    }

    @Test
    public void canDeleteUser() {
        User userToDelete = createUser(TEST_EMAIL);
        Persistence.users.removeById(userToDelete.id);
        User user = Persistence.users.getById(userToDelete.id);
        assertNull(user, "Deleted User should no longer exist in database (should return as null).");
    }

    //    http://localhost:4567/plan?userId=b46266f7-a461-421b-8e92-01d99b945ab0&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=

    @Test
    public void canCreateTripRequest() {
        String userId = "123456";
        TripRequest tripRequest = createTripRequest(userId);
        String id = tripRequest.id;
        TripRequest retrieved = Persistence.tripRequest.getById(id);
        assertEquals(id, retrieved.id, "Found Trip request ID should equal inserted ID.");
        // tidy up
        Persistence.tripRequest.removeById(tripRequest.id);
    }

    @Test
    public void canDeleteTripRequest() {
        String userId = "123456";
        TripRequest tripRequestToDelete = createTripRequest(userId);
        Persistence.tripRequest.removeById(tripRequestToDelete.id);
        TripRequest tripRequest = Persistence.tripRequest.getById(tripRequestToDelete.id);
        assertNull(tripRequest, "Deleted TripRequest should no longer exist in database (should return as null).");
    }

    @Test
    public void canCreateTripSummaryWithError() {
        TripSummary tripSummary = createTripSummaryWithError();
        TripSummary retrieved = Persistence.tripSummary.getById(tripSummary.id);
        System.out.println("Retrieved trip summary with error:" + retrieved.toString());
        assertEquals(tripSummary.id, retrieved.id, "Found Trip summary ID should equal inserted ID.");
        // tidy up
        Persistence.tripSummary.removeById(tripSummary.id);
    }

    @Test
    public void canCreateTripSummary() {
        TripSummary tripSummary = createTripSummary();
        TripSummary retrieved = Persistence.tripSummary.getById(tripSummary.id);
        System.out.println("Retrieved trip summary:" + retrieved.toString());
        assertEquals(tripSummary.id, retrieved.id, "Found Trip summary ID should equal inserted ID.");
        // tidy up
        Persistence.tripSummary.removeById(tripSummary.id);
    }

    @Test
    public void canDeleteTripSummary() {
        TripSummary tripSummaryToDelete = createTripSummary();
        Persistence.tripSummary.removeById(tripSummaryToDelete.id);
        TripSummary tripSummary = Persistence.tripSummary.getById(tripSummaryToDelete.id);
        assertNull(tripSummary, "Deleted trip summary should no longer exist in database (should return as null).");
    }

    @Test
    public void canGetFilteredTripRequestsWithFromAndToDate() {
        int limit = 3;
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        User user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        LocalDateTime fromStartOfDay = LocalDate.now().atTime(LocalTime.MIN);
        LocalDateTime toEndOfDay = LocalDate.now().atTime(LocalTime.MAX);

        Bson filter = Filters.and(gte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME, Date.from(fromStartOfDay.atZone(ZoneId.systemDefault()).toInstant())),
            lte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME, Date.from(toEndOfDay.atZone(ZoneId.systemDefault()).toInstant())),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id));

        List<TripRequest> result = Persistence.tripRequest.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(),tripRequests.size());

        // tidy up
        deleteTripRequests(tripRequests);
    }

    @Test
    public void canGetFilteredTripRequestsFromDate() {
        int limit = 3;
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        User user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        LocalDateTime fromStartOfDay = LocalDate.now().atTime(LocalTime.MIN);

        Bson filter = Filters.and(gte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME, Date.from(fromStartOfDay.atZone(ZoneId.systemDefault()).toInstant())),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id));

        List<TripRequest> result = Persistence.tripRequest.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(),tripRequests.size());

        // tidy up
        deleteTripRequests(tripRequests);
    }

    @Test
    public void canGetFilteredTripRequestsToDate() {
        int limit = 3;
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        User user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        LocalDateTime toEndOfDay = LocalDate.now().atTime(LocalTime.MAX);

        Bson filter = Filters.and(
            lte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME, Date.from(toEndOfDay.atZone(ZoneId.systemDefault()).toInstant())),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id));

        List<TripRequest> result = Persistence.tripRequest.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(),tripRequests.size());

        // tidy up
        deleteTripRequests(tripRequests);
    }

    @Test
    public void canGetFilteredTripRequestsForUser() {
        int limit = 3;
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        User user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        Bson filter = Filters.and(
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id));

        List<TripRequest> result = Persistence.tripRequest.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(),tripRequests.size());

        // tidy up
        deleteTripRequests(tripRequests);
    }

    @Test
    public void canGetFilteredTripRequestsForUserWithMaxLimit() {
        int limit = 10;
        int max = 5;
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        User user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        Bson filter = Filters.and(
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id));

        List<TripRequest> result = Persistence.tripRequest.getFilteredWithLimit(filter, max);
        assertEquals(result.size(),max);

        // tidy up
        deleteTripRequests(tripRequests);
    }

}
