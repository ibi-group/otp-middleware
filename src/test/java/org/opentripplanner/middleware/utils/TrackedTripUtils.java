package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.triptracker.TripTrackingData;

import java.util.Date;

public class TrackedTripUtils {
    /**
     * The mobile app sends timestamps in seconds which is then converted into milliseconds in {@link TripTrackingData}.
     * To represent this in testing, provide the time in seconds from epoch.
     */
    public static Date getDateAndConvertToSeconds() {
        return new Date(new Date().getTime() / 1000);
    }
}
