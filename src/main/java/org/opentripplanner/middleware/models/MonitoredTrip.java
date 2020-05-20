package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.otp.core.api.model.Itinerary;

import java.util.List;
import java.util.Set;

/**
 * A monitored trip represents
 */
public class MonitoredTrip extends Model {

    /**
     * User Id. Id of user monitoring trip.
     */
    public String userId;

    /**
     * Name given to the trip by the user
     */
    public String tripName;

    /**
     * The time at which the trip takes place. This will be in the format HH:mm and is extracted (or provided separately)
     * to the date and time within the query parameters. The reasoning is so that it doesn't have to be extracted every
     * time the trip requires checking.
     */
    public String tripTime;

    /**
     * The number of minutes prior to a trip taking place that the status should be checked.
     */
    public int leadTimeInMinutes;

    /**
     * The days on which the trip status should be checked. This will be a text representation of each day of the week.
     * Enums have not been used because they are not compatible with MongoDB at this point in time. Java's number
     * representation hasn't been used because it is irrelevant to calling systems.
     *
     * Expecting each effected day no more than once with the following representation:
     *
     * "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
     */
    public Set<String> days;

    /**
     * Specify if the monitored trip should be checked on a federal holiday.
     */
    //TODO define federal holiday source!
    public boolean excludeFederalHolidays;

    /**
     * Specify if the monitored trip is active. If true, the trip will be checked.
     */
    public boolean isActive = true;

    /**
     * Query params. Query parameters influencing trip.
     */
    public String queryParams;

    /**
     * The trips itinerary
     */
    public List<Itinerary> itinerary;

    //TODO
    /**
    notificationThresholds
    notifyRouteChange (true/false) - Instead of notificationThresholds
    arrivalDelayMinutesThreshold (int minutes) - Instead of notificationThresholds
    departureDelayMinutesThreshold (int minutes) - Instead of notificationThresholds
    notifyDelayToTrip (true/false)
    */

    public MonitoredTrip() {
    }
}

