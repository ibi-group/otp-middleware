package org.opentripplanner.middleware.persistence;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.persistence.PersistenceUtil.*;

/**
 * Tests to verify that trip request and trip summary persistence in MongoDB collections are functioning properly. A
 * number of {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class TripHistoryPersistenceTest extends OtpMiddlewareTest {
    private static final String TEST_EMAIL = "john.doe@example.com";

    TripRequest tripRequest = null;
    TripSummary tripSummary = null;
    List<TripRequest> tripRequests = null;

    @Test
    public void canCreateTripRequest() {
        String userId = "123456";
        tripRequest = createTripRequest(userId);
        String id = tripRequest.id;
        TripRequest retrieved = Persistence.tripRequests.getById(id);
        assertEquals(id, retrieved.id, "Found Trip request ID should equal inserted ID.");
    }

    @Test
    public void canDeleteTripRequest() {
        String userId = "123456";
        TripRequest tripRequestToDelete = createTripRequest(userId);
        Persistence.tripRequests.removeById(tripRequestToDelete.id);
        TripRequest tripRequest = Persistence.tripRequests.getById(tripRequestToDelete.id);
        assertNull(tripRequest, "Deleted TripRequest should no longer exist in database (should return as null).");
    }

    @Test
    public void canCreateTripSummaryWithError() throws IOException {
        tripSummary = createTripSummaryWithError();
        TripSummary retrieved = Persistence.tripSummaries.getById(tripSummary.id);
        assertEquals(tripSummary.id, retrieved.id, "Found Trip summary ID should equal inserted ID.");
    }

    @Test
    public void canCreateTripSummary() throws IOException {
        tripSummary = createTripSummary();
        TripSummary retrieved = Persistence.tripSummaries.getById(tripSummary.id);
        assertEquals(tripSummary.id, retrieved.id, "Found Trip summary ID should equal inserted ID.");
    }

    @Test
    public void canDeleteTripSummary() throws IOException {
        TripSummary tripSummaryToDelete = createTripSummary();
        Persistence.tripSummaries.removeById(tripSummaryToDelete.id);
        TripSummary tripSummary = Persistence.tripSummaries.getById(tripSummaryToDelete.id);
        assertNull(tripSummary, "Deleted trip summary should no longer exist in database (should return as null).");
    }

    @Test
    public void canGetFilteredTripRequestsWithFromAndToDate() {
        int limit = 3;
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        OtpUser user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        LocalDateTime fromStartOfDay = DateTimeUtils.nowAsLocalDate().atTime(LocalTime.MIN);
        LocalDateTime toEndOfDay = DateTimeUtils.nowAsLocalDate().atTime(LocalTime.MAX);

        Bson filter = Filters.and(
            gte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME,
                Date.from(fromStartOfDay
                    .atZone(DateTimeUtils.getSystemZoneId())
                    .toInstant())),
            lte(TRIP_REQUEST_DATE_CREATED_FIELD_NAME,
                Date.from(toEndOfDay
                    .atZone(DateTimeUtils.getSystemZoneId())
                    .toInstant())),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id));

        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsFromDate() {
        int limit = 3;
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        OtpUser user = createUser(TEST_EMAIL);

        List<TripRequest> tripRequests = createTripRequests(limit, user.id);

        LocalDateTime fromStartOfDay = DateTimeUtils.nowAsLocalDate().atTime(LocalTime.MIN);

        Bson filter = Filters.and(
            gte(
                TRIP_REQUEST_DATE_CREATED_FIELD_NAME,
                Date.from(fromStartOfDay.atZone(DateTimeUtils.getSystemZoneId()).toInstant())
            ),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id)
        );

        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsToDate() {
        int limit = 3;
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        OtpUser user = createUser(TEST_EMAIL);

        tripRequests = createTripRequests(limit, user.id);

        LocalDateTime toEndOfDay = DateTimeUtils.nowAsLocalDate().atTime(LocalTime.MAX);

        Bson filter = Filters.and(
            lte(
                TRIP_REQUEST_DATE_CREATED_FIELD_NAME,
                Date.from(toEndOfDay.atZone(DateTimeUtils.getSystemZoneId()).toInstant())
            ),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id)
        );

        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsForUser() {
        int limit = 3;
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        OtpUser user = createUser(TEST_EMAIL);

        tripRequests = createTripRequests(limit, user.id);

        Bson filter = Filters.eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id);

        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsForUserWithMaxLimit() {
        int limit = 10;
        int max = 5;
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

        OtpUser user = createUser(TEST_EMAIL);

        tripRequests = createTripRequests(limit, user.id);

        Bson filter = Filters.eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id);

        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, max);
        assertEquals(result.size(), max);
    }

    @AfterEach
    public void remove() {
        if (tripRequest != null) {
            Persistence.tripRequests.removeById(tripRequest.id);
        }

        if (tripSummary != null) {
            Persistence.tripSummaries.removeById(tripSummary.id);
        }

        if (tripRequests != null) {
            deleteTripRequests(tripRequests);
        }
    }
}
