package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.RoutingError;
import org.opentripplanner.middleware.otp.response.TripPlan;

import java.util.Date;
import java.util.List;

/**
 * A trip summary represents the parts of an OTP plan response which are required for trip monitoring purposes
 */
public class TripSummary extends Model {
    private static final long serialVersionUID = 1L;

    public Date date;

    public Place fromPlace;

    public Place toPlace;

    public List<RoutingError> errors;

    public List<Itinerary> itineraries;

    public String tripRequestId;

    public String batchId;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripSummary() {
    }

    public TripSummary(TripPlan tripPlan, List<RoutingError> errors, String tripRequestId, String batchId) {
        if (tripPlan != null) {
            this.date = tripPlan.date;
            this.fromPlace = tripPlan.from;
            this.toPlace = tripPlan.to;
            this.itineraries = tripPlan.itineraries;
        }
        this.errors = errors;
        this.tripRequestId = tripRequestId;
        this.batchId = batchId;
    }

    @Override
    public String toString() {
        return "TripSummary{" +
            "date=" + date +
            ", fromPlace=" + fromPlace +
            ", toPlace=" + toPlace +
            ", error=" + errors +
            ", itineraries=" + itineraries +
            ", tripRequestId='" + tripRequestId + '\'' +
            ", batchId='" + batchId + '\'' +
            '}';
    }
}
