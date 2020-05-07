package org.opentripplanner.middleware.models;

import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.model.error.PlannerError;

import java.util.Date;
import java.util.List;

public class TripSummary extends Model {
    private static final long serialVersionUID = 1L;

    private final String userId;

    /**
     * Time stamp. Time at which the request was made.
     */
    // TODO: Perhaps not needed as Model -> dateCreated would be the same
    private final Date timestamp;

    /**
     * From place. Trip starting point.
     */
    private final Place fromPlace;

    /**
     * To place. Trip end point.
     */
    private final Place toPlace;

    private final PlannerError error;

    private final List<Itinerary> itinerary;

    public TripSummary(String userId, Date timestamp, Place fromPlace, Place toPlace, PlannerError error, List<Itinerary> itinerary) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.fromPlace = fromPlace;
        this.toPlace = toPlace;
        this.error = error;
        this.itinerary = itinerary;
    }

    public String getUserId() {
        return userId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Place getFromPlace() {
        return fromPlace;
    }

    public Place getToPlace() {
        return toPlace;
    }

    public PlannerError getError() {
        return error;
    }

    public List<Itinerary> getItinerary() {
        return itinerary;
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
