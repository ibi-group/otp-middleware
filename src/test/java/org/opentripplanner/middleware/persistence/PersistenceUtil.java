package org.opentripplanner.middleware.persistence;

import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Utility class to aid with creating and storing objects in Mongo.
 */
public class PersistenceUtil {

    private static final String BATCH_ID = "783726";
    private static final String TRIP_REQUEST_ID = "59382";

    private static Response PLAN_RESPONSE = null;
    private static Response PLAN_ERROR_RESPONSE = null;


    /**
     * Create Otp user and store in database.
     */
    public static OtpUser createUser(String email) {
        OtpUser user = new OtpUser();
        user.email = email;
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
     * Get Api user from database
     */
    public static ApiUser getApiUser(String id) {
        return Persistence.apiUsers.getById(id);
    }

    /**
     * Remove Api user from database
     */
    public static void removeApiUser(String id) {
        Persistence.apiUsers.removeById(id);
    }

    /**
     * Remove Admin user from database
     */
    public static void removeAdminUser(String id) {
        Persistence.adminUsers.removeById(id);
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
    public static TripSummary createTripSummary() {
        TripSummary tripSummary = new TripSummary(PLAN_RESPONSE.plan, PLAN_RESPONSE.error, TRIP_REQUEST_ID);
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create trip summary from static plan error response file and store in database.
     */
    public static TripSummary createTripSummaryWithError() {
        TripSummary tripSummary = new TripSummary(null, PLAN_ERROR_RESPONSE.error, TRIP_REQUEST_ID);
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

    /**
     * Delete multiple trip requests from database.
     */
    public static void deleteTripRequests(List<TripRequest> tripRequests) {
        for (TripRequest tripRequest: tripRequests) {
            Persistence.tripRequests.removeById(tripRequest.id);
        }
    }

    public static MonitoredTrip createMonitoredTrip(String userId) {
        MonitoredTrip monitoredTrip = new MonitoredTrip();
        monitoredTrip.userId = userId;
        monitoredTrip.tripName = "Commute to work";
        monitoredTrip.tripTime = "07:30";
        monitoredTrip.leadTimeInMinutes = 30;
        monitoredTrip.monday = true;
        monitoredTrip.tuesday = true;
        monitoredTrip.wednesday = true;
        monitoredTrip.thursday = true;
        monitoredTrip.friday = true;
        monitoredTrip.excludeFederalHolidays = true;
        monitoredTrip.queryParams = "fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";

        Itinerary itinerary = new Itinerary();
        itinerary.duration = 1350L;
        itinerary.elevationGained = 0.0;
        itinerary.elevationLost = 0.0;
        itinerary.endTime = new Date();
        itinerary.startTime = new Date();
        itinerary.transfers = 0;
        itinerary.transitTime = 150;
        itinerary.waitingTime = 2;
        itinerary.walkDistance = 1514.13182088778;
        itinerary.walkLimitExceeded = false;

        Leg leg = new Leg();
        leg.startTime = new Date();
        leg.endTime = new Date();
        leg.departureDelay = 10;
        leg.arrivalDelay = 10;
        leg.realTime = true;
        leg.distance = 1500.0;
        leg.pathway = true;
        leg.mode = "walk";

        Place place = new Place();
        place.lat = 28.5398938204469;
        place.lon = -81.3772773742676;
        place.name = "28.54894, -81.38971";
        place.orig = "28.54894, -81.38971";
        leg.from = place;
        leg.to = place;

        List<Leg> legs = new ArrayList<>();
        legs.add(leg);
        itinerary.legs = legs;

        monitoredTrip.itinerary = itinerary;

        Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    /**
     * Get plan responses from file for creating trip summaries.
     */
    public static void stagePlanResponses() {
        final String filePath = "src/test/resources/org/opentripplanner/middleware/";
        PLAN_RESPONSE = FileUtils.getFileContentsAsJSON(filePath + "planResponse.json", Response.class);
        PLAN_ERROR_RESPONSE = FileUtils.getFileContentsAsJSON(filePath + "planErrorResponse.json", Response.class);
    }

    public static int getStatusCodeFromResponse(String url, HttpUtils.REQUEST_METHOD requestMethod) {
        HttpResponse<String> stringHttpResponse = HttpUtils.httpRequestRawResponse(
            URI.create(url),
            1000,
            requestMethod,
            new HashMap<>(),
            ""
        );

        return stringHttpResponse.statusCode();
    }
}
