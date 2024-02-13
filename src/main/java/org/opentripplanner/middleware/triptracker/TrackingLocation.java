package org.opentripplanner.middleware.triptracker;

import java.time.ZonedDateTime;
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

    public TrackingLocation(Double lat, Double lon, Date timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }

    /** Used in testing **/
    public TrackingLocation(String dateTime, String lat, String lon) {
        this.timestamp = new Date(ZonedDateTime.parse(dateTime).toInstant().toEpochMilli());
        this.lat = Double.parseDouble(lat);
        this.lon = Double.parseDouble(lon);
    }

    /** Used in testing **/
    public TrackingLocation(String dateTime, int minutes, int seconds, String lat, String lon) {
        this.timestamp = new Date(ZonedDateTime
            .parse(dateTime)
            .plusMinutes(minutes)
            .plusSeconds(seconds)
            .toInstant()
            .toEpochMilli()
        );
        this.lat = Double.parseDouble(lat);
        this.lon = Double.parseDouble(lon);
    }
}
