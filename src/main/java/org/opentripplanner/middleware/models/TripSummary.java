package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.PlannerError;
import org.opentripplanner.middleware.otp.response.TripPlan;

import java.util.List;

/**
 * A trip summary represents the parts of an OTP plan response which are required for trip monitoring purposes
 */
public class TripSummary extends Model {
    private static final long serialVersionUID = 1L;
    public Place fromPlace;

    public Place toPlace;

    public PlannerError error;

    public List<Itinerary> itineraries;

    public String tripRequestId;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripSummary() {
    }

    public TripSummary(TripPlan tripPlan, PlannerError error, String tripRequestId) {

        if (tripPlan != null) {
            this.fromPlace = tripPlan.from;
            this.toPlace = tripPlan.to;
            this.itineraries = tripPlan.itineraries;
        }

        this.error = error;
        this.tripRequestId = tripRequestId;

    }

    @Override
    public String toString() {
        return "TripSummary{" +
            "fromPlace=" + fromPlace +
            ", toPlace=" + toPlace +
            ", error=" + error +
            ", itineraries=" + itineraries +
            ", tripRequestId='" + tripRequestId + '\'' +
            ", id='" + id + '\'' +
            ", lastUpdated=" + lastUpdated +
            ", dateCreated=" + dateCreated +
            '}';
    }
}
