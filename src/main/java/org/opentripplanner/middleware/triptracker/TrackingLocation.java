package org.opentripplanner.middleware.triptracker;

import java.util.Date;
import java.util.Objects;

/**
 * A user's location details.
 */
public class TrackingLocation {

    public int bearing;

    public Double lat;

    public Double lon;

    public int speed;

    public Date timestamp;

    public TrackingLocation() {
    }

    public TrackingLocation(int bearing, Double lat, Double lon, int speed, Date timestamp) {
        this.bearing = bearing;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    /**
     * The time stamp has been omitted from the equals and hashCode methods. This is to prevent duplicate location
     * details being stored.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackingLocation that = (TrackingLocation) o;
        return bearing == that.bearing && speed == that.speed && Objects.equals(lat, that.lat) && Objects.equals(lon, that.lon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bearing, lat, lon, speed);
    }
}
