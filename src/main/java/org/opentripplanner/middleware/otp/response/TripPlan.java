package org.opentripplanner.middleware.otp.response;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A TripPlan is a set of ways to get from point A to point B at time T.
 * Pare down version of class original produced for OpenTripPlanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripPlan {

    /**  The time and date of travel */
    public Date date = null;

    /** The origin */
    public Place from = null;

    /** The destination */
    public Place to = null;

    /** A list of possible itineraries */
    @JsonProperty(value="itineraries")
    public List<Itinerary> itinerary = new ArrayList<>();

    public TripPlan() { }

    public TripPlan(Place from, Place to, Date date) {
        this.from = from;
        this.to = to;
        this.date = date;
    }

    public void addItinerary(Itinerary itinerary) {
        this.itinerary.add(itinerary);
    }

    @Override
    public String toString() {
        return "TripPlan{" +
                "date=" + date +
                ", from=" + from +
                ", to=" + to +
                ", itinerary=" + itinerary +
                '}';
    }
}
