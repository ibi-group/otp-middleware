package org.opentripplanner.middleware.triptracker;

/**
 * A user's location details.
 */
public class TrackingLocation {

    public int bearing;

    public Double lat;

    public Double lon;

    public int speed;

    public long timestamp;

    public TrackingLocation() {
    }

    public TrackingLocation(int bearing, Double lat, Double lon, int speed, long timestamp) {
        this.bearing = bearing;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.timestamp = timestamp;
    }
}
