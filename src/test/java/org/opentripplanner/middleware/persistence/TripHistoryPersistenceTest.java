package org.opentripplanner.middleware.persistence;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
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
    private static final int LIMIT = 3;
    private static final String TEST_EMAIL = "john.doe@example.com";
    private static final String TRIP_REQUEST_DATE_CREATED_FIELD_NAME = "dateCreated";
    private static final String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";

    private static OtpUser otpUser = null;
    private static TripRequest tripRequest = null;
    private static TripSummary tripSummary = null;
    private static TripSummary tripSummaryWithError = null;
    private static List<TripRequest> tripRequests = null;

    @BeforeAll
    public static void setup() throws IOException {
        otpUser = createUser(TEST_EMAIL);
        tripRequest = createTripRequest(otpUser.id);
        tripRequests = createTripRequests(LIMIT, otpUser.id);
        tripSummary = createTripSummary(tripRequest.id);
        tripSummaryWithError = createTripSummaryWithError(tripRequest.id);
    }

    @AfterAll
    public static void tearDown() {
        if (otpUser != null) otpUser.delete(false);
        if (tripSummaryWithError != null) Persistence.tripSummaries.removeById(tripSummaryWithError.id);
    }

    @Test
    public void canCreateTripRequest() {
        if (tripRequest == null) tripRequest = createTripRequest(otpUser.id);
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
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, otpUser.id));
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, LIMIT);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsFromDate() {
        LocalDateTime fromStartOfDay = DateTimeUtils.nowAsLocalDate().atTime(LocalTime.MIN);
        Bson filter = Filters.and(
            gte(
                TRIP_REQUEST_DATE_CREATED_FIELD_NAME,
                Date.from(fromStartOfDay.atZone(DateTimeUtils.getSystemZoneId()).toInstant())
            ),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, otpUser.id)
        );
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, LIMIT);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsToDate() {
        LocalDateTime toEndOfDay = DateTimeUtils.nowAsLocalDate().atTime(LocalTime.MAX);
        Bson filter = Filters.and(
            lte(
                TRIP_REQUEST_DATE_CREATED_FIELD_NAME,
                Date.from(toEndOfDay.atZone(DateTimeUtils.getSystemZoneId()).toInstant())
            ),
            eq(TRIP_REQUEST_USER_ID_FIELD_NAME, otpUser.id)
        );
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, LIMIT);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsForUser() {
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
        Bson filter = Filters.eq(TRIP_REQUEST_USER_ID_FIELD_NAME, otpUser.id);
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, LIMIT);
        assertEquals(result.size(), tripRequests.size());
    }

    @Test
    public void canGetFilteredTripRequestsForUserWithMaxLimit() {
        int max = 2;
        String TRIP_REQUEST_USER_ID_FIELD_NAME = "userId";
        Bson filter = Filters.eq(TRIP_REQUEST_USER_ID_FIELD_NAME, otpUser.id);
        List<TripRequest> result = Persistence.tripRequests.getFilteredWithLimit(filter, max);
        assertEquals(result.size(), max);
    }
}
