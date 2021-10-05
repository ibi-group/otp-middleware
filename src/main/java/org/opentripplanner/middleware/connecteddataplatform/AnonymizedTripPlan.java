package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.TripPlan} containing only parameters
 * flagged as anonymous.
 */
public class AnonymizedTripPlan {
    /**
     * The time and date of travel
     */
    public Date date = null;

    /**
     * A list of possible itineraries
     */
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
    public AnonymizedTripPlan(
        Date date,
        List<Itinerary> itineraries,
        Coordinates fromCoordinates,
        Coordinates toCoordinates
    ) {
        this.date = date;
        itineraries.forEach(itinerary -> {
            AnonymizedItinerary itin = new AnonymizedItinerary();
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
                anonymizedLeg.from = getAnonymizedPlace(
                    leg.transitLeg,
                    isFirstOrLastLegOfTrip,
                    leg.from,
                    fromCoordinates
                );
                anonymizedLeg.to = getAnonymizedPlace(
                    leg.transitLeg,
                    isFirstOrLastLegOfTrip,
                    leg.to,
                    toCoordinates
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
        boolean isTransitLeg,
        boolean isFirstOrLastLegOfTrip,
        Place place,
        Coordinates coordinates
    ) {
        AnonymizedPlace anonymizedPlace = new AnonymizedPlace();
        anonymizedPlace.arrival = place.arrival;
        anonymizedPlace.departure = place.departure;
        Coordinates placeCoordinates = new Coordinates(place.lat, place.lon);
        if (isTransitLeg) {
            anonymizedPlace.coordinates = placeCoordinates;
            anonymizedPlace.name = place.name;
            anonymizedPlace.stopCode = place.stopCode;
            anonymizedPlace.stopId = place.stopId;
            anonymizedPlace.stopSequence = place.stopSequence;
        } else {
            // replace lat/lon values with the lat/lon values created for the trip request. The start and end legs
            // will then be consistent with the trip's 'to' and 'from' place.
            anonymizedPlace.coordinates = (isFirstOrLastLegOfTrip) ? coordinates : placeCoordinates;
        }
        return anonymizedPlace;
    }
}
