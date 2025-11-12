package org.opentripplanner.middleware.stats;

import org.opentripplanner.middleware.models.Model;

import java.util.Date;
import java.util.Objects;

/**
 * Holds anonymous statistics collected on a daily basis.
 */
public class DailyStats extends Model {
    /**
     * The date for which the stats apply.
     */
    public Date date;

    /**
     * The total number of {@link org.opentripplanner.middleware.models.OtpUser}
     * at the time the statistics are collected.
     */
    public long otpUsers;

    /**
     * The number of {@link org.opentripplanner.middleware.models.TripRequest} by logged-in users
     * for the date the statistics are collected.
     */
    public long tripRequests;

    /**
     * The number of {@link org.opentripplanner.middleware.models.OtpUser} associated with the
     * {@link org.opentripplanner.middleware.models.TripRequest}
     * for the date the statistics are collected.
     */
    public long otpUsersWithTripRequests;

    // Insert other stats here as needed and update equals().

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof DailyStats)) return false;
        if (other == this) return true;

        DailyStats otherStats = (DailyStats) other;
        return otherStats.date.equals(this.date) &&
            otherStats.otpUsers == this.otpUsers &&
            otherStats.tripRequests == this.tripRequests &&
            otherStats.otpUsersWithTripRequests == this.otpUsersWithTripRequests;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, tripRequests, otpUsersWithTripRequests);
    }

}
