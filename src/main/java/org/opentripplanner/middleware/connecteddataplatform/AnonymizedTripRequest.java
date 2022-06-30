package org.opentripplanner.middleware.connecteddataplatform;

import com.mongodb.client.FindIterable;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
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
            itineraries.addAll(getItineraries(tripSummaryId++, tripSummary));
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
        TripSummary tripSummary
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
            if (itinerary.legs != null) {
                processLegCoordinates(itinerary.legs);
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
                    anonymizedLeg.fromStop = leg.from.stopId;
                    anonymizedLeg.from = new Coordinates(leg.from.lat, leg.from.lon);
                    anonymizedLeg.toStop = leg.to.stopId;
                    anonymizedLeg.to = new Coordinates(leg.to.lat, leg.to.lon);
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
            }
            anonymizedItineraries.add(itin);
        }
        return anonymizedItineraries;
    }

    /**
     * Define the coordinates for all legs. If the leg is non-transit and is before the first transit leg or after
     * the last transit leg the coordinates must be removed for privacy. Any transit legs or non-transit legs
     * between the first and last transit legs, the coordinates can be provided.
     */
    private void processLegCoordinates(List<Leg> legs) {
        int firstTransitLeg = getFirstTransitLeg(legs);
        // No need to find the last transit leg if the first transit leg indicates that all legs are non-transit.
        int lastTransitLeg = (firstTransitLeg == Integer.MAX_VALUE) ? Integer.MIN_VALUE : getLastTransitLeg(legs);
        for (int i = 0; i <= legs.size() -1; i++) {
            Leg leg = legs.get(i);
            if (Boolean.TRUE.equals(!leg.transitLeg) && (i < firstTransitLeg || i > lastTransitLeg)) {
                // The non-transit leg is before the first transit leg or after the last transit leg, remove the
                // coordinates.
                removeCoordinatesFromLeg(leg);
            }
        }
    }

    /**
     * Define the position of the first transit leg. If all legs are non-transit return {@link Integer#MAX_VALUE} to
     * represent this. This will then force the calling method to remove coordinates from all legs.
     */
    private int getFirstTransitLeg(List<Leg> legs) {
        for (int i = 0; i <= legs.size(); i++) {
            if (Boolean.TRUE.equals(legs.get(i).transitLeg)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Define the position of the last transit leg. If all legs are non-transit return {@link Integer#MIN_VALUE} to
     * represent this. This will then force the calling method to remove coordinates from all legs.
     */
    private int getLastTransitLeg(List<Leg> legs) {
        for (int i = legs.size() -1; i >= 0; i--) {
            if (Boolean.TRUE.equals(legs.get(i).transitLeg)) {
                return i;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Remove the coordinate values from a leg.
     */
    private void removeCoordinatesFromLeg(Leg leg) {
        leg.from.lat = null;
        leg.from.lon = null;
        leg.to.lat = null;
        leg.to.lon = null;
    }
}
