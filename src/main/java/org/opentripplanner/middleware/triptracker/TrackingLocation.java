package org.opentripplanner.middleware.triptracker;

import java.time.Instant;
import java.util.Date;

/**
 * A user's location details.
 */
public class TrackingLocation {

    public int bearing;

    public Double lat;

    public Double lon;

    public int speed;

    public Date timestamp;

    public TripStatus tripStatus;

    public TrackingLocation() {
        // Needed for deserializing objects.
    }

    public TrackingLocation(int bearing, Double lat, Double lon, int speed, Date timestamp) {
        this.bearing = bearing;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public TrackingLocation(Double lat, Double lon, Date timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }

    /** Used in testing **/
    public TrackingLocation(Instant instant, double lat, double lon) {
        this(lat, lon, new Date(instant.toEpochMilli()));
    }

    /** Used in testing **/
    public TrackingLocation(Date timestamp, double lat, double lon) {
        this(lat, lon, timestamp);
    }
}
