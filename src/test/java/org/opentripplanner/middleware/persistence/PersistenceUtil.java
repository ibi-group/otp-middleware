package org.opentripplanner.middleware.persistence;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.core.api.model.Itinerary;
import org.opentripplanner.middleware.otp.core.api.model.Leg;
import org.opentripplanner.middleware.otp.core.api.model.Place;
import org.opentripplanner.middleware.otp.core.api.model.TripPlan;
import org.opentripplanner.middleware.otp.core.api.resource.Response;

import java.util.*;

import static org.opentripplanner.middleware.OtpDispatcherHelper.getPlanErrorFromOtp;
import static org.opentripplanner.middleware.OtpDispatcherHelper.getPlanFromOtp;

public class PersistenceUtil {

    private static final String batchId = "783726";
    private static final String tripRequestId = "59382";

    /**
     * Utility to create user and store in database.
     */
    public static OtpUser createUser(String email) {
        OtpUser user = new OtpUser();
        user.email = email;
        Persistence.otpUsers.create(user);
        return user;
    }

    public static TripRequest createTripRequest(String userId) {
        String fromPlace = "28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034";
        String toPlace = "28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759";
        String queryParams = "arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";
        TripRequest tripRequest = new TripRequest(userId, batchId, fromPlace, toPlace, queryParams);
        Persistence.tripRequest.create(tripRequest);
        return tripRequest;
    }

    public static TripSummary createTripSummary() {
        OtpDispatcherResponse response = getPlanFromOtp();
        Response otpResponse = response.getResponse();
        TripSummary tripSummary = new TripSummary(otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary, tripRequestId);
        Persistence.tripSummary.create(tripSummary);
        System.out.println("Saved trip summary:" + tripSummary.toString());
        return tripSummary;
    }

    public static TripSummary createTripSummaryWithError() {
        OtpDispatcherResponse response = getPlanErrorFromOtp();
        Response otpResponse = response.getResponse();
        TripPlan tripPlan = otpResponse.getPlan();
        TripSummary tripSummary;
        if (tripPlan != null) {
            tripSummary = new TripSummary(otpResponse.getPlan().from, otpResponse.getPlan().to, otpResponse.getError(), otpResponse.getPlan().itinerary, tripRequestId);
        } else {
            tripSummary = new TripSummary(otpResponse.getError(), tripRequestId);
        }
        Persistence.tripSummary.create(tripSummary);
        System.out.println("Saved trip summary:" + tripSummary.toString());
        return tripSummary;
    }

    public static List<TripRequest> createTripRequests(int amount, String userId) {
        List<TripRequest> tripRequests = new ArrayList<>();
        int i = 0;
        while (i < amount) {
            tripRequests.add(createTripRequest(userId));
            i++;
        }
        return tripRequests;
    }

    public static void deleteTripRequests(List<TripRequest> tripRequests) {
        for (TripRequest tripRequest: tripRequests) {
            Persistence.tripRequest.removeById(tripRequest.id);
        }
    }

    public static MonitoredTrip createMonitoredTrip(String userId) {
        Set<String> days = new HashSet<>();
        days.add("monday");
        days.add("tuesday");
        days.add("wednesday");
        days.add("thursday");
        days.add("friday");

        MonitoredTrip monitoredTrip = new MonitoredTrip();
        monitoredTrip.userId = userId;
        monitoredTrip.tripName = "Commute to work";
        monitoredTrip.tripTime = "07:30";
        monitoredTrip.leadTimeInMinutes = 30;
        monitoredTrip.days = days;
        monitoredTrip.excludeFederalHolidays = true;
        monitoredTrip.queryParams = "userId=b46266f7-a461-421b-8e92-01d99b945ab0&fromPlace=28.54894%2C%20-81.38971%3A%3A28.548944048426772%2C-81.38970606029034&toPlace=28.53989%2C%20-81.37728%3A%3A28.539893820446867%2C-81.37727737426759&date=2020-05-05&time=12%3A04&arriveBy=false&mode=WALK%2CBUS%2CRAIL&showIntermediateStops=true&maxWalkDistance=1207&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true&companies=";

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

        List<Itinerary> itineraries = new ArrayList<>();
        itineraries.add(itinerary);
        monitoredTrip.itinerary = itineraries;

        Persistence.monitoredTrip.create(monitoredTrip);
        return monitoredTrip;
    }

}
