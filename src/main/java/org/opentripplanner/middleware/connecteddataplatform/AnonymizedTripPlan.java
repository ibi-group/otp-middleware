package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Itinerary;
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
    public AnonymizedTripPlan(Date date, List<Itinerary> itineraries) {
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
            itinerary.legs.forEach(leg -> {
                AnonymizedLeg anonymizedLeg = new AnonymizedLeg();
                anonymizedLeg.interlineWithPreviousLeg = leg.interlineWithPreviousLeg;
                anonymizedLeg.mode = leg.mode;
                anonymizedLeg.realTime = leg.realTime;
                anonymizedLeg.routeId = leg.routeId;
                anonymizedLeg.routeShortName = leg.routeShortName;
                anonymizedLeg.routeLongName = leg.routeLongName;
                anonymizedLeg.routeType = leg.routeType;
                anonymizedLeg.transitLeg = leg.transitLeg;
                anonymizedLeg.tripId = leg.tripId;
                anonymizedLeg.from = getAnonymizedPlace(leg.from);
                anonymizedLeg.to = getAnonymizedPlace(leg.to);
                itin.legs.add(anonymizedLeg);
            });
            this.itineraries.add(itin);
        });
    }

    /**
     * Create an {@link AnonymizedPlace} containing required anonymous parameters from {@link Place}.
     */
    private AnonymizedPlace getAnonymizedPlace(Place place) {
        AnonymizedPlace anonymizedPlace = new AnonymizedPlace();
        anonymizedPlace.arrival = place.arrival;
        anonymizedPlace.departure = place.departure;
        anonymizedPlace.lon = place.lon;
        anonymizedPlace.lat = place.lat;
        anonymizedPlace.name = place.name;
        anonymizedPlace.stopCode = place.stopCode;
        anonymizedPlace.stopId = place.stopId;
        anonymizedPlace.stopSequence = place.stopSequence;
        return anonymizedPlace;
    }
}
