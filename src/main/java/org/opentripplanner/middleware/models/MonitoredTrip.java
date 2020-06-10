package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.otp.response.Itinerary;

import java.util.List;
import java.util.Set;

/**
 * A monitored trip represents a trip a user would like to receive notification on if affected by a delay and/or
 * route change.
 */
public class MonitoredTrip extends Model {

    /**
     * Mongo Id of user monitoring trip.
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
     *
     * All use of this parameter will require equalsIgnoreCase() to avoid missing days.
     */
    public Set<String> days;

    /**
     * Specify if the monitored trip should be checked on a US federal holiday.
     */
    //TODO define US federal holiday source
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
    public Itinerary itinerary;

    //TODO, agree on and implement these parameters
    /**
    notificationThresholds
    notifyRouteChange (true/false) - Instead of notificationThresholds
    arrivalDelayMinutesThreshold (int minutes) - Instead of notificationThresholds
    departureDelayMinutesThreshold (int minutes) - Instead of notificationThresholds
    notifyDelayToTrip (true/false)
    */

    public MonitoredTrip() {
    }

    /**
     * Confirm that the user updating a monitored trip has the required permissions
     */
    @Override
    public boolean canBeManagedBy(Auth0UserProfile user) {
        // If the user is attempting to update someone else's monitored trip, they must be admin.
        boolean isManagingSelf = false;

        if (user.otpUser != null) {
            isManagingSelf = userId.equalsIgnoreCase(user.otpUser.id);
        } else if (user.apiUser != null) {
            isManagingSelf = userId.equalsIgnoreCase(user.apiUser.id);
        }

        if (isManagingSelf) {
            return true;
        } else if (user.adminUser != null) {
            // If not managing self, user must have manage permission.
            for (Permission permission : user.adminUser.permissions) {
                if (permission.canManage(this.getClass())) return true;
            }
        }
        // Fallback to Model#userCanManage.
        return super.canBeManagedBy(user);
    }

}

