package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.core.api.model.Itinerary;
import org.opentripplanner.middleware.otp.core.api.model.Place;
import org.opentripplanner.middleware.otp.core.api.model.error.PlannerError;

import java.util.Date;
import java.util.List;

/**
 * A trip summary represents the parts of an OTP plan response which are required for trip monitoring purposes
 */
public class TripSummary extends Model {
    private static final long serialVersionUID = 1L;
    public String userId;

    // TODO: Perhaps not needed as Model -> dateCreated would be the same
    public Date timestamp;

    public Place fromPlace;

    public Place toPlace;

    public PlannerError error;

    public List<Itinerary> itinerary;

    /** This no-arg constructor exists to make MongoDB happy. */
    public TripSummary() {
    }

    public TripSummary(String userId, Place fromPlace, Place toPlace, PlannerError error, List<Itinerary> itinerary) {
        this.userId = userId;
        this.timestamp = new Date();
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.error = error;
        this.itinerary = itinerary;
    }

    public TripSummary(String userId, PlannerError error) {
        this.userId = userId;
        this.timestamp = new Date();
        this.error = error;
    }

    @Override
    public String toString() {
        return "TripSummary{" +
                "userId='" + userId + '\'' +
                ", timestamp=" + timestamp +
                ", fromPlace=" + fromPlace +
                ", toPlace=" + toPlace +
                ", error=" + error +
                ", itinerary=" + itinerary +
                '}';
    }
}
