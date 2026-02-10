package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Agency;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Route;
import org.opentripplanner.middleware.otp.response.Trip;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.Date;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Leg} containing only parameters
 * that don't contain precise user or location data.
 */
public class AnonymizedLeg {

    // Parameters for both transit and non transit legs.
    public Double distance;
    public Double duration;
    public Date startTime;
    public Date endTime;
    public String mode;
    public Boolean transitLeg;
    public String fromStop;
    public Coordinates from;
    public String toStop;
    public Coordinates to;

    // Parameters for a transit leg.
    public Boolean interlineWithPreviousLeg;
    public Boolean realTime;
    public Agency agency;
    public Route route;
    public Trip trip;

    // Parameters for non transit leg.
    public Boolean rentedVehicle;

    public AnonymizedLeg() {
    }

    public AnonymizedLeg(Leg leg) {
        // Parameters for both transit and non transit legs.
        this.distance = leg.distance;
        this.duration = leg.duration;
        this.startTime = leg.startTime;
        this.endTime = leg.endTime;
        this.mode = leg.mode;
        this.transitLeg = leg.transitLeg;
        this.fromStop = (leg.from.stop != null) ? leg.from.stop.id : null;
        this.from = getLegCoordinates(leg.from);
        this.toStop = (leg.to.stop != null) ? leg.to.stop.id : null;
        this.to = getLegCoordinates(leg.to);
        if (Boolean.TRUE.equals(leg.transitLeg)) {
            // Parameters for a transit leg.
            this.interlineWithPreviousLeg = leg.interlineWithPreviousLeg;
            this.realTime = leg.realTime;
            this.agency = (leg.agency != null) ? leg.agency : null;
            this.route = (leg.route != null) ? leg.route : null;
            this.trip = (leg.trip != null) ? leg.trip : null;
        } else {
            // Parameters for non transit leg.
            this.rentedVehicle = leg.rideHailingEstimate != null;
        }
    }

    /**
     * Only provide the leg coordinates if both lat/lon values are available.
     */
    private Coordinates getLegCoordinates(Place place) {
        return (place.lat != null && place.lon != null) ? new Coordinates(place.lat, place.lon) : null;
    }

}
