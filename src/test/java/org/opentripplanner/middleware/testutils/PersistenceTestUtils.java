package org.opentripplanner.middleware.testutils;

import org.opentripplanner.middleware.otp.graphql.TransportMode;
import org.opentripplanner.middleware.otp.graphql.QueryVariables;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.models.AdminUser;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.ItineraryExistence;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
        user.notificationChannel.add(OtpUser.Notification.EMAIL);
        user.hasConsentedToTerms = true;
        user.storeTripHistory = true;
        user.pushDevices = 0;
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

    public static TripRequest createTripRequest(String userId) {
        return createTripRequest(userId, BATCH_ID, null);
    }

    public static TripRequest createTripRequest(String userId, LocalDateTime createDate) {
        return createTripRequest(userId, BATCH_ID, createDate);
    }

    /**
     * Create trip request and store in database.
     */
    public static TripRequest createTripRequest(String userId, String batchId, LocalDateTime createDate) {
        if (createDate != null) {
            return createTripRequest(userId, batchId, DateTimeUtils.convertToDate(createDate), null);
        } else {
            return createTripRequest(userId, batchId, null, null);
        }
    }

    /**
     * Create trip request and store in database.
     */
    public static TripRequest createTripRequest(String userId, String batchId, Date createDate, String mode) {
        return createTripRequest(userId, batchId, createDate, mode, true);
    }

    /**
     * Create trip request and store in database.
     */
    public static TripRequest createTripRequest(String userId, String batchId, Date createDate, String mode, boolean provideMode) {
        String fromPlace = "Airport, College Park, GA, USA :: 33.64070037704429,-84.44622866991179";
        String toPlace = "177 Gibson Street SE, Atlanta, GA, USA :: 33.748893261983575,-84.35611735540574";

        QueryVariables queryVariables = new QueryVariables();
        queryVariables.fromPlace = fromPlace;
        queryVariables.toPlace = toPlace;
        queryVariables.date = "2021-09-22";
        queryVariables.time = "15:54";
        queryVariables.walkSpeed = 1.34F;
        if (provideMode) {
            String[] modes = (mode != null ? mode : "WALK,BUS,RAIL").split(",");
            queryVariables.modes = Arrays.stream(modes)
                .map(TransportMode::new)
                .collect(Collectors.toList());
        }

        TripRequest tripRequest = new TripRequest(userId, batchId, queryVariables);
        if (createDate != null) {
            tripRequest.dateCreated = createDate;
        }
        Persistence.tripRequests.create(tripRequest);
        return tripRequest;
    }

    /**
     * Create trip summary with default batch id and date.
     */
    public static TripSummary createTripSummary(String tripRequestId) throws Exception {
        return createTripSummary(tripRequestId, BATCH_ID, null);
    }

    /**
     * Create trip summary with default batch id.
     */
    public static TripSummary createTripSummary(String tripRequestId, LocalDateTime createDate) throws Exception {
        return createTripSummary(tripRequestId, BATCH_ID, createDate);
    }

    /**
     * Create trip summary from static plan response file and store in database.
     */
    public static TripSummary createTripSummary(String tripRequestId, String batchId, LocalDateTime createDate) throws Exception {
        OtpResponse planResponse = OtpTestUtils.OTP2_DISPATCHER_PLAN_RESPONSE.getOtp2Response();
        TripSummary tripSummary = new TripSummary(planResponse.plan, planResponse.error, tripRequestId, batchId);
        if (createDate != null) {
            tripSummary.dateCreated = DateTimeUtils.convertToDate(createDate);
        }
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create trip summary from static plan error response file and store in database.
     */
    public static TripSummary createTripSummaryWithError(String tripRequestId) throws Exception {
        return createTripSummaryWithError(tripRequestId, BATCH_ID, null);
    }

    /**
     * Create trip summary from static plan error response file and store in database.
     */
    public static TripSummary createTripSummaryWithError(String tripRequestId, String batchId, LocalDateTime createDate) throws Exception {
        OtpResponse planErrorResponse = OtpTestUtils.OTP_DISPATCHER_PLAN_ERROR_RESPONSE.getResponse();
        TripSummary tripSummary = new TripSummary(null, planErrorResponse.error, tripRequestId, batchId);
        if (createDate != null) {
            tripSummary.dateCreated = DateTimeUtils.convertToDate(createDate);
        }
        Persistence.tripSummaries.create(tripSummary);
        return tripSummary;
    }

    /**
     * Create multiple trip requests and store in database.
     */
    public static List<TripRequest> createTripRequests(int amount, String userId) {
        return createTripRequests(amount, userId, null);
    }

    /**
     * Create multiple trip requests and store in database.
     */
    public static List<TripRequest> createTripRequests(int amount, String userId, LocalDateTime createDate) {
        List<TripRequest> tripRequests = new ArrayList<>();
        int i = 0;
        while (i < amount) {
            tripRequests.add(createTripRequest(userId, createDate));
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

        monitoredTrip.itinerary = createItinerary();

        Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    public static MonitoredTrip createMonitoredTrip(
        String userId,
        OtpDispatcherResponse otpDispatcherResponse,
        boolean persist,
        JourneyState journeyState
    ) throws Exception {
        MonitoredTrip monitoredTrip = new MonitoredTrip(OtpTestUtils.getSampleQueryParams(), otpDispatcherResponse);
        monitoredTrip.userId = userId;
        monitoredTrip.tripName = "test trip";
        monitoredTrip.leadTimeInMinutes = 240;
        // set trip time since otpDispatcherResponse doesn't have full query params in URI
        monitoredTrip.tripTime = "08:35";
        monitoredTrip.updateWeekdays(true);
        monitoredTrip.itineraryExistence = new ItineraryExistence();
        if (journeyState != null) monitoredTrip.journeyState = journeyState;
        if (persist) Persistence.monitoredTrips.create(monitoredTrip);
        return monitoredTrip;
    }

    public static void deleteMonitoredTrip(MonitoredTrip trip) {
        Persistence.monitoredTrips.removeById(trip.id);
    }

    static Itinerary createItinerary() {
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

    public static void deleteOtpUser(OtpUser... optUsers) {
        for (OtpUser otpUser : optUsers) {
            if (otpUser != null) {
                OtpUser user = Persistence.otpUsers.getById(otpUser.id);
                if (user != null) {
                    user.delete(user.auth0UserId != null);
                }
            }
        }
    }
}
