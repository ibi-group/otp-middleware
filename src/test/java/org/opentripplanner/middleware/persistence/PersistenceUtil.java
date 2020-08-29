package org.opentripplanner.middleware.persistence;

import org.opentripplanner.middleware.TestUtils;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Response;

import java.io.IOException;
import java.util.*;

/**
 * Utility class to aid with creating and storing objects in Mongo.
 */
public class PersistenceUtil {

    private static final String BATCH_ID = "783726";
    private static final String TRIP_REQUEST_ID = "59382";

    private static final String resourceFilePath = "persistence/";


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
    public static TripSummary createTripSummary() throws IOException {
        Response planResponse = getPlanResponse();
        TripSummary tripSummary = new TripSummary(planResponse.plan, planResponse.error, TRIP_REQUEST_ID);
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create trip summary from static plan error response file and store in database.
     */
    public static TripSummary createTripSummaryWithError() throws IOException {
        Response planErrorResponse = getPlanErrorResponse();
        TripSummary tripSummary = new TripSummary(null, planErrorResponse.error, TRIP_REQUEST_ID);
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
        for (TripRequest tripRequest : tripRequests) {
            Persistence.tripRequests.removeById(tripRequest.id);
        }
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

        monitoredTrip.itinerary = createItinerary();

        Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    public static MonitoredTrip createMonitoredTrip(String userId, OtpDispatcherResponse otpDispatcherResponse) {
        MonitoredTrip monitoredTrip = new MonitoredTrip(otpDispatcherResponse);
        monitoredTrip.userId = userId;
        monitoredTrip.tripName = "test trip";
        monitoredTrip.leadTimeInMinutes = 30;
        Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    private static Itinerary createItinerary() {
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
        return itinerary;
    }

    public static void deleteMonitoredTripAndJourney(MonitoredTrip trip) {
        trip.clearJourneyState();
        Persistence.monitoredTrips.removeById(trip.id);
    }

    /**
     * Get successful plan response from file for creating trip summaries.
     */
    public static Response getPlanResponse() throws IOException {
        return TestUtils.getResourceFileContentsAsJSON(resourceFilePath + "planResponse.json", Response.class);
    }

    /**
     * Get error plan response from file for creating trip summaries.
     */
    public static Response getPlanErrorResponse() throws IOException {
        return TestUtils.getResourceFileContentsAsJSON(resourceFilePath + "planErrorResponse.json", Response.class);
    }
}
