package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.TripPlan} containing only parameters
 * flagged as anonymous.
 */
public class AnonymizedTripPlan {
    /**  The time and date of travel */
    public Date date = null;

    /** A list of possible itineraries */
    public List<AnonymizedItinerary> itineraries = new ArrayList<>();

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedTripPlan() {
    }

    /**
     * Create an {@link AnonymizedTripPlan} containing {@link AnonymizedItinerary}s and {@link AnonymizedLeg}s from
     * required anonymous parameters in related classes.
     */
    public AnonymizedTripPlan(Date date, List<Itinerary> itineraries, AnonymizedTripRequest anonymizedTripRequest) {
        this.date = date;
        itineraries.forEach(itinerary -> {
            AnonymizedItinerary itin = new AnonymizedItinerary();
            itin.duration = itinerary.duration;
            itin.startTime = itinerary.startTime;
            itin.endTime = null;
            itin.transfers = 0;
            itin.transitTime = 0;
            itin.waitingTime = 0;
            itin.walkDistance = 0.0;
            itin.walkTime = 0;
            for (int i=0; i<itinerary.legs.size(); i++) {
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
                anonymizedLeg.from = getAnonymizedPlace(
                    true,
                    leg.transitLeg,
                    isFirstOrLastLegOfTrip,
                    leg.from,
                    anonymizedTripRequest
                );
                anonymizedLeg.to = getAnonymizedPlace(
                    false,
                    leg.transitLeg,
                    isFirstOrLastLegOfTrip,
                    leg.to,
                    anonymizedTripRequest
                );
                if (leg.transitLeg) {
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
                    anonymizedLeg.hailedCar = leg.hailedCar;
                    anonymizedLeg.rentedBike = leg.rentedBike;
                    anonymizedLeg.rentedCar = leg.rentedCar;
                    anonymizedLeg.rentedVehicle = leg.rentedVehicle;
                }
                itin.legs.add(anonymizedLeg);
            }
            this.itineraries.add(itin);
        });
    }

    /**
     * Create an {@link AnonymizedPlace} containing required anonymous parameters from {@link Place}.
     */
    private AnonymizedPlace getAnonymizedPlace(
        boolean isFromPlace,
        boolean isTransitLeg,
        boolean isFirstOrLastLegOfTrip,
        Place place,
        AnonymizedTripRequest anonymizedTripRequest
    ) {
        AnonymizedPlace anonymizedPlace = new AnonymizedPlace();
        anonymizedPlace.arrival = place.arrival;
        anonymizedPlace.departure = place.departure;
        if (isTransitLeg) {
            anonymizedPlace.lon = place.lon;
            anonymizedPlace.lat = place.lat;
            anonymizedPlace.name = place.name;
            anonymizedPlace.stopCode = place.stopCode;
            anonymizedPlace.stopId = place.stopId;
            anonymizedPlace.stopSequence = place.stopSequence;
        } else {
            // non transit leg
            if (isFirstOrLastLegOfTrip) {
                // replace lat/lon values with the randomized values created for the trip request. The start and end legs
                // will then be consistent with the trip's 'to' and 'from' place.
                anonymizedPlace.lon = (isFromPlace) ? anonymizedTripRequest.fromPlaceLon : anonymizedTripRequest.toPlaceLon;
                anonymizedPlace.lat = (isFromPlace) ? anonymizedTripRequest.fromPlaceLat : anonymizedTripRequest.toPlaceLat;
            } else {
                anonymizedPlace.lon = place.lon;
                anonymizedPlace.lat = place.lat;
            }
        }
        return anonymizedPlace;
    }
}
