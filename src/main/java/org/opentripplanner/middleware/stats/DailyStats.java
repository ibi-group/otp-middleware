package org.opentripplanner.middleware.stats;

import org.opentripplanner.middleware.models.Model;

import java.time.LocalDate;

/**
 * Holds anonymous statistics collected on a daily basis.
 */
public class DailyStats extends Model {
    /**
     * The date for which the stats apply.
     */
    public LocalDate date;

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

    // Insert other stats here as needed.
}
