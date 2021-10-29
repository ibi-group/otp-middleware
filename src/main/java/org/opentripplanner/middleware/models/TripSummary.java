package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.PlannerError;
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

    public PlannerError error;

    public List<Itinerary> itineraries;

    public String tripRequestId;

    public String batchId;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripSummary() {
    }

    public TripSummary(TripPlan tripPlan, PlannerError error, String tripRequestId, String batchId) {
        if (tripPlan != null) {
            this.date = tripPlan.date;
            this.fromPlace = tripPlan.from;
            this.toPlace = tripPlan.to;
            this.itineraries = tripPlan.itineraries;
        }
        this.error = error;
        this.tripRequestId = tripRequestId;
        this.batchId = batchId;
    }

    @Override
    public String toString() {
        return "TripSummary{" +
            "date=" + date +
            ", fromPlace=" + fromPlace +
            ", toPlace=" + toPlace +
            ", error=" + error +
            ", itineraries=" + itineraries +
            ", tripRequestId='" + tripRequestId + '\'' +
            ", batchId='" + batchId + '\'' +
            '}';
    }
}
