package org.opentripplanner.middleware.testUtils;

import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.OtpResponse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Utility class to aid with creating and storing objects in Mongo.
 */
public class PersistenceTestUtils {

    private static final String BATCH_ID = "783726";

    /**
     * Create Otp user and store in database.
     */
    public static OtpUser createUser(String email) {
        return createUser(email, null);
    }

    public static OtpUser createUser(String email, String phoneNumber) {
        OtpUser user = new OtpUser();
        user.email = email;
        user.phoneNumber = phoneNumber;
        user.notificationChannel = "email";
        user.hasConsentedToTerms = true;
        user.storeTripHistory = true;
        Persistence.otpUsers.create(user);
        return user;
    }

    /**
     * Create Api user and store in database.
     */
    public static ApiUser createApiUser(String email) {
        ApiUser user = new ApiUser();
        user.email = email;
        Persistence.apiUsers.create(user);
        return user;
    }

    /**
     * Create Admin user and store in database.
     */
    public static AdminUser createAdminUser(String email) {
        AdminUser user = new AdminUser();
        user.email = email;
        Persistence.adminUsers.create(user);
        return user;
    }

    /**
     * Create trip request and store in database.
     */
    public static TripRequest createTripRequest(String userId) {
        String fromPlace = "28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034";
        String toPlace = "28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759";
        String queryParams = "arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";
        TripRequest tripRequest = new TripRequest(userId, BATCH_ID, fromPlace, toPlace, queryParams);
        Persistence.tripRequests.create(tripRequest);
        return tripRequest;
    }

    /**
     * Create trip summary from static plan response file and store in database.
     */
    public static TripSummary createTripSummary(String tripRequestId) throws IOException {
        OtpResponse planResponse = OtpTestUtils.getPlanResponse();
        TripSummary tripSummary = new TripSummary(planResponse.plan, planResponse.error, tripRequestId);
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create trip summary from static plan error response file and store in database.
     */
    public static TripSummary createTripSummaryWithError(String tripRequestId) throws IOException {
        OtpResponse planErrorResponse = OtpTestUtils.getPlanErrorResponse();
        TripSummary tripSummary = new TripSummary(null, planErrorResponse.error, tripRequestId);
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create multiple trip requests and store in database.
     */
    public static List<TripRequest> createTripRequests(int amount, String userId) {
        List<TripRequest> tripRequests = new ArrayList<>();
        int i = 0;
        while (i < amount) {
            tripRequests.add(createTripRequest(userId));
            i++;
        }
        return tripRequests;
    }

    public static MonitoredTrip createMonitoredTrip(String userId) {
        MonitoredTrip monitoredTrip = new MonitoredTrip();
        monitoredTrip.userId = userId;
        monitoredTrip.tripName = "Commute to work";
        monitoredTrip.tripTime = "07:30";
        monitoredTrip.leadTimeInMinutes = 30;
        monitoredTrip.updateWeekdays(true);
        monitoredTrip.excludeFederalHolidays = true;
        monitoredTrip.queryParams = "fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";

        monitoredTrip.itinerary = OtpTestUtils.createItinerary();

        Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    public static MonitoredTrip createMonitoredTrip(
        String userId,
        OtpDispatcherResponse otpDispatcherResponse,
        boolean persist
    ) throws URISyntaxException {
        MonitoredTrip monitoredTrip = new MonitoredTrip(otpDispatcherResponse);
        monitoredTrip.userId = userId;
        monitoredTrip.tripName = "test trip";
        monitoredTrip.leadTimeInMinutes = 240;
        // set trip time since otpDispatcherResponse doesn't have full query params in URI
        monitoredTrip.tripTime = "08:35";
        monitoredTrip.updateWeekdays(true);
        if (persist) Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    public static void deleteMonitoredTrip(MonitoredTrip trip) {
        Persistence.monitoredTrips.removeById(trip.id);
    }

}
