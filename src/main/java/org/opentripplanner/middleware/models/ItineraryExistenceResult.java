package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.List;

/**
 * This class holds the OTP response for each day of the week,
 * so that clients can check on itinerary existence for
 */
public class ItineraryExistenceResult {
    public List<Itinerary> monday;
    public List<Itinerary> tuesday;
    public List<Itinerary> wednesday;
    public List<Itinerary> thursday;
    public List<Itinerary> friday;
    public List<Itinerary> saturday;
    public List<Itinerary> sunday;
}
