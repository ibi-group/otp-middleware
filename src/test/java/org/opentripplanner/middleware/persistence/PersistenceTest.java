package org.opentripplanner.middleware.persistence;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.otp.OtpDispatcherImpl;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.core.api.model.TripPlan;
import org.opentripplanner.middleware.otp.core.api.resource.Response;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that persistence in MongoDB collections are functioning properly. A number of
 * {@link TypedPersistence} methods are tested here, but the HTTP endpoints defined in
 * {@link org.opentripplanner.middleware.controllers.api.ApiController} are not themselves tested here.
 */
public class PersistenceTest extends OtpMiddlewareTest {
    private static final String TEST_EMAIL = "john.doe@example.com";
    private static final String OTP_SERVER = "https://fdot-otp-server.ibi-transit.com";
    private static final String OTP_SERVER_PLAN_END_POINT = "/otp/routers/default/plan";

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

    /**
     * Utility to create user and store in database.
     */
    private User createUser(String email) {
        User user = new User();
        user.email = email;
        Persistence.users.create(user);
        return user;
    }

    //    http://localhost:4567/plan?userId=b46266f7-a461-421b-8e92-01d99b945ab0&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=

    @Test
    public void canCreateTripRequest() {
        TripRequest tripRequest = createTripRequest();
        String id = tripRequest.id;
        TripRequest retrieved = Persistence.tripRequest.getById(id);
        System.out.println("Trip request retrieved:" + retrieved);
        assertEquals(id, retrieved.id, "Found Trip request ID should equal inserted ID.");
        // tidy up
        Persistence.tripRequest.removeById(tripRequest.id);
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

    private OtpDispatcherResponse getPlanFromOtp() {
        OtpDispatcherImpl otpDispatcher = new OtpDispatcherImpl(OTP_SERVER);
        OtpDispatcherResponse response = otpDispatcher.getPlan("plan?userId=123456&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=", OTP_SERVER_PLAN_END_POINT);
        System.out.println("OTP Plan response:" + response.toString());
        return response;
    }

    // fromPalce instead of fromPlace to produce error
    private OtpDispatcherResponse getPlanErrorFromOtp() {
        OtpDispatcherImpl otpDispatcher = new OtpDispatcherImpl(OTP_SERVER);
        OtpDispatcherResponse response = otpDispatcher.getPlan("plan?userId=123456&fromPalce=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=", OTP_SERVER_PLAN_END_POINT);
        System.out.println("OTP Plan error response:" + response.toString());
        return response;
    }

    private TripSummary createTripSummaryWithError() {
        OtpDispatcherResponse response = getPlanErrorFromOtp();
        Response otpResponse = response.getResponse();
        String userId = "123456";
        TripPlan tripPlan = otpResponse.getPlan();
        TripSummary tripSummary;
        if (tripPlan != null)
            tripSummary = new TripSummary(userId, otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary);
        else
            tripSummary = new TripSummary(userId, otpResponse.getError());

        Persistence.tripSummary.create(tripSummary);
        System.out.println("Saved trip summary:" + tripSummary.toString());
        return tripSummary;
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

    private TripSummary createTripSummary() {
        OtpDispatcherResponse response = getPlanFromOtp();
        Response otpResponse = response.getResponse();
        String userId = "123456";
        TripSummary tripSummary = new TripSummary(userId, otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary);
        Persistence.tripSummary.create(tripSummary);
        System.out.println("Saved trip summary:" + tripSummary.toString());
        return tripSummary;
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


}
