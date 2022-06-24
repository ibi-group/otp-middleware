package org.opentripplanner.middleware.connecteddataplatform;

import com.mongodb.client.FindIterable;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.PlannerError;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.models.TripRequest} containing only parameters
 * that don't contain precise user or location data.
 */
public class AnonymizedTripRequest {

    /**
     * Batch Id. Id for trip requests planned together but representing different modes.
     */
    public String batchId;

    /**
     * From place. Trip starting point.
     */
    public Coordinates fromPlace;

    /**
     * To place. Trip end point.
     */
    public Coordinates toPlace;

    /** The date the trip request was made. */
    public String date;

    /** The time the trip request was made. */
    public String time;

    /** Either 'arrive by' or 'depart at'. */
    public AnonymousTripType tripType;

    /** Transit modes selected by user. */
    public List<String> mode;

    /** Maximum walking distance defined by user. */
    public String maxWalkDistance;

    /** Trip optimization specified by user. */
    public String optimize;

    /**
     * A list of possible itineraries for all trip summaries.
     */
    public List<AnonymizedItinerary> itineraries = new ArrayList<>();

    /**
     * Trip plan error.
     */
    public PlannerError error;

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripRequest() {
    }

    public AnonymizedTripRequest(
        TripRequest tripRequest,
        FindIterable<TripSummary> tripSummaries,
        Coordinates fromCoordinates,
        Coordinates toCoordinates
    ) {
        this.batchId = tripRequest.batchId;
        this.fromPlace = fromCoordinates;
        this.toPlace = toCoordinates;
        if (tripRequest.requestParameters != null) {
            this.date = tripRequest.requestParameters.get("date");
            this.time = tripRequest.requestParameters.get("time");
            String tripRequestType = tripRequest.requestParameters.get("arriveBy");
            if (tripRequestType != null && tripRequestType.equalsIgnoreCase("true")) {
                this.tripType = AnonymousTripType.ARRIVE_BY;
            } else if (tripRequestType != null && tripRequestType.equalsIgnoreCase("false")) {
                this.tripType = AnonymousTripType.DEPART_AT;
            }
            this.mode = getModes(tripRequest.requestParameters.get("mode"));
            this.maxWalkDistance = tripRequest.requestParameters.get("maxWalkDistance");
            this.optimize = tripRequest.requestParameters.get("optimize");
        }

        // Extract all trip summary itineraries, convert to anonymized itineraries and group.
        int tripSummaryId = 1;
        for (TripSummary tripSummary : tripSummaries) {
            if (tripSummary.error != null) {
                // If trip summary has an error, add it to the anonymized trip request and don't attempt to process the
                // trip summary itineraries, because there won't be any.
                this.error = tripSummary.error;
                break;
            }
            itineraries.addAll(getItineraries(tripSummaryId++, tripSummary, fromCoordinates, toCoordinates));
        }
    }

    /**
     * Extract modes from the trip request and return as an array.
     */
    public List<String> getModes(String tripRequestModes) {
        return Arrays.asList(tripRequestModes.split(","));
    }

    /**
     * Extract trip summary itineraries.
     */
    private List<AnonymizedItinerary> getItineraries(
        int tripSummaryId,
        TripSummary tripSummary,
        Coordinates fromCoordinates,
        Coordinates toCoordinates
    ) {
        List<AnonymizedItinerary> anonymizedItineraries = new ArrayList<>();
        if (tripSummary.itineraries == null) {
            return anonymizedItineraries;
        }
        int itineraryId = 1;
        for (Itinerary itinerary : tripSummary.itineraries) {
            AnonymizedItinerary itin = new AnonymizedItinerary();
            itin.tripSummaryId = tripSummaryId;
            itin.itineraryId = itineraryId++;
            itin.duration = itinerary.duration;
            itin.startTime = itinerary.startTime;
            itin.endTime = itinerary.endTime;
            itin.transfers = itinerary.transfers;
            itin.transitTime = itinerary.transitTime;
            itin.waitingTime = itinerary.waitingTime;
            itin.walkDistance = itinerary.walkDistance;
            itin.walkTime = itinerary.walkTime;
            for (int i = 0; i < itinerary.legs.size(); i++) {
                Leg leg = itinerary.legs.get(i);
                AnonymizedLeg anonymizedLeg = new AnonymizedLeg();
                // Parameters for both transit and non transit legs.
                anonymizedLeg.distance = leg.distance;
                anonymizedLeg.duration = leg.duration;
                anonymizedLeg.startTime = leg.startTime;
                anonymizedLeg.endTime = leg.endTime;
                anonymizedLeg.mode = leg.mode;
                anonymizedLeg.transitLeg = leg.transitLeg;
                boolean isFirstOrLastLegOfTrip = i == 0 || i == itinerary.legs.size() - 1;
                anonymizedLeg.fromStop = leg.from.stopId;
                anonymizedLeg.from = getPlaceCoordinates(leg.transitLeg, isFirstOrLastLegOfTrip, leg.from, fromCoordinates);
                anonymizedLeg.toStop = leg.to.stopId;
                anonymizedLeg.to = getPlaceCoordinates(leg.transitLeg, isFirstOrLastLegOfTrip, leg.to, toCoordinates);
                if (Boolean.TRUE.equals(leg.transitLeg)) {
                    // Parameters for a transit leg.
                    anonymizedLeg.agencyId = leg.agencyId;
                    anonymizedLeg.interlineWithPreviousLeg = leg.interlineWithPreviousLeg;
                    anonymizedLeg.realTime = leg.realTime;
                    anonymizedLeg.routeId = leg.routeId;
                    anonymizedLeg.routeShortName = leg.routeShortName;
                    anonymizedLeg.routeLongName = leg.routeLongName;
                    anonymizedLeg.routeType = leg.routeType;
                    anonymizedLeg.tripBlockId = leg.tripBlockId;
                    anonymizedLeg.tripId = leg.tripId;
                } else {
                    // Parameters for non transit leg.
                    anonymizedLeg.rentedVehicle = leg.rentedVehicle;
                }
                itin.legs.add(anonymizedLeg);
            }
            anonymizedItineraries.add(itin);
        }
        return anonymizedItineraries;
    }

    /**
     * Create {@link Coordinates} for a place. Replace lat/lon values with the lat/lon values created for the trip
     * request if not a transit leg. The start and end legs will then be consistent with the trip's 'to' and 'from'
     * place.
     */
    private Coordinates getPlaceCoordinates(
        boolean isTransitLeg,
        boolean isFirstOrLastLegOfTrip,
        Place place,
        Coordinates tripRequestCoordinates
    ) {
        return (!isTransitLeg && isFirstOrLastLegOfTrip) ? tripRequestCoordinates : new Coordinates(place.lat, place.lon);
    }

}
