package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

import java.util.List;

/**
 * A monitored trip represents a trip a user would like to receive notification on if affected by a delay and/or route
 * change.
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
     * The time at which the trip takes place. This will be in the format HH:mm and is extracted (or provided
     * separately) to the date and time within the query parameters. The reasoning is so that it doesn't have to be
     * extracted every time the trip requires checking.
     */
    public String tripTime;

    /**
     * The number of minutes prior to a trip taking place that the status should be checked.
     */
    public int leadTimeInMinutes;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean monday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean tuesday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean wednesday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean thursday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean friday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean saturday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean sunday;

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
     * notificationThresholds notifyRouteChange (true/false) - Instead of notificationThresholds
     * arrivalDelayMinutesThreshold (int minutes) - Instead of notificationThresholds departureDelayMinutesThreshold
     * (int minutes) - Instead of notificationThresholds notifyDelayToTrip (true/false)
     */

    public MonitoredTrip() {
    }

    @Override
    public boolean canBeCreatedBy(Auth0UserProfile profile) {
        OtpUser otpUser = profile.otpUser;
        if (userId == null) {
            if (otpUser == null) {
                // The otpUser must exist (and be the requester) if the userId is null. Otherwise, there is nobody to
                // assign the trip to.
                return false;
            }
            // If userId on trip is null, auto-assign the otpUser's id to trip.
            userId = otpUser.id;
        } else {
            // If userId was provided, follow authorization provided by canBeManagedBy
            return canBeManagedBy(profile);
        }
        return super.canBeCreatedBy(profile);
    }

    /**
     * Confirm that the requesting user has the required permissions
     */
    @Override
    public boolean canBeManagedBy(Auth0UserProfile user) {
        // This should not be possible, but return false on a null userId just in case.
        if (userId == null) return false;
        // If the user is attempting to update someone else's monitored trip, they must be admin.
        boolean belongsToUser = false;
        // Monitored trip can only be owned by an OtpUser (not an ApiUser or AdminUser).
        if (user.otpUser != null) {
            belongsToUser = userId.equals(user.otpUser.id);
        }

        if (belongsToUser) {
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

    /**
     * Get monitored trips for the specified {@link OtpUser} user Id.
     */
    public static List<MonitoredTrip> tripsForUser(String userId) {
        return Persistence.monitoredTrips.getFiltered(TypedPersistence.filterByUserId(userId));
    }

    @Override
    public boolean delete() {
        // TODO: Add journey state deletion.
        return Persistence.monitoredTrips.removeById(this.id);
    }
}

