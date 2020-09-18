package org.opentripplanner.middleware.persistence;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
    private static final int limit = 3;
    private static final String userId = "123456";
    private static final String TEST_EMAIL = "john.doe@example.com";

    private static OtpUser user = null;
    private static TripRequest tripRequest = null;
    private static TripSummary tripSummary = null;
    private static TripSummary tripSummaryWithError = null;
    private static List<TripRequest> tripRequests = null;

    @BeforeAll
    public static void setup() throws IOException {
        user = createUser(TEST_EMAIL);
        tripRequest = createTripRequest(userId);
        tripSummary = createTripSummary();
        tripSummaryWithError = createTripSummaryWithError();
        tripRequests = createTripRequests(limit, user.id);
    }

    @AfterAll
    public static void tearDown() {
        if (user != null) Persistence.otpUsers.removeById(user.id);
        if (tripRequest != null) Persistence.tripRequests.removeById(tripRequest.id);
        if (tripSummary != null) Persistence.tripSummaries.removeById(tripSummary.id);
        if (tripSummaryWithError != null) Persistence.tripSummaries.removeById(tripSummaryWithError.id);
        if (tripRequests != null) deleteTripRequests(tripRequests);
    }

    @Test
    public void canCreateTripRequest() {
        if (tripRequest == null) tripRequest = createTripRequest(userId);
        TripRequest retrieved = Persistence.tripRequests.getById(tripRequest.id);
        assertEquals(tripRequest.id, retrieved.id, "Found Trip request ID should equal inserted ID.");
    }

    @Test
    public void canDeleteTripRequest() {
        Persistence.tripRequests.removeById(tripRequest.id);
        TripRequest deletedTripRequest = Persistence.tripRequests.getById(tripRequest.id);
        tripRequest = null;
        assertNull(deletedTripRequest, "Deleted TripRequest should no longer exist in database (should return as null).");
    }

    @Test
    public void canCreateTripSummaryWithError() {
        TripSummary retrieved = Persistence.tripSummaries.getById(tripSummaryWithError.id);
        assertEquals(tripSummaryWithError.id, retrieved.id, "Found Trip summary ID should equal inserted ID.");
    }

    @Test
    public void canCreateTripSummary() {
        TripSummary retrieved = Persistence.tripSummaries.getById(tripSummary.id);
        assertEquals(tripSummary.id, retrieved.id, "Found Trip summary ID should equal inserted ID.");
    }

    @Test
    public void canDeleteTripSummary() {
        Persistence.tripSummaries.removeById(tripSummary.id);
        TripSummary deletedTripSummary = Persistence.tripSummaries.getById(tripSummary.id);
        assertNull(deletedTripSummary, "Deleted trip summary should no longer exist in database (should return as null).");
    }

    @Test
    public void canGetFilteredTripRequestsWithFromAndToDate() {
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
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
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
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
        String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
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
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
        Bson filter = Filters.eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id);
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, limit);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsForUserWithMaxLimit() {
        int max = 2;
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
        Bson filter = Filters.eq(TRIP_REQUEST_USER_ID_FIELD_NAME, user.id);
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, max);
        assertEquals(result.size(), max);
    }

}
