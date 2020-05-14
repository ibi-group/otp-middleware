package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.core.api.model.Itinerary;
import org.opentripplanner.middleware.otp.core.api.model.Place;
import org.opentripplanner.middleware.otp.core.api.model.error.PlannerError;
import java.util.List;

/**
 * A trip summary represents the parts of an OTP plan response which are required for trip monitoring purposes
 */
public class TripSummary extends Model {
    private static final long serialVersionUID = 1L;
    public Place fromPlace;

    public Place toPlace;

    public PlannerError error;

    public List<Itinerary> itinerary;

    public String tripRequestId;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripSummary() {
    }

    public TripSummary(Place fromPlace, Place toPlace, PlannerError error, List<Itinerary> itinerary, String tripRequestId) {
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.error = error;
        this.itinerary = itinerary;
        this.tripRequestId = tripRequestId;
    }

    public TripSummary(PlannerError error, String tripRequestId) {
        this.error = error;
        this.tripRequestId = tripRequestId;
    }

    @Override
    public String toString() {
        return "TripSummary{" +
                "fromPlace=" + fromPlace +
                ", toPlace=" + toPlace +
                ", error=" + error +
                ", itinerary=" + itinerary +
                ", tripRequestId='" + tripRequestId + '\'' +
                '}';
    }
}
