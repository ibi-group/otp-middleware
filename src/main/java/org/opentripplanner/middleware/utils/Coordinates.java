package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.leonard.Position;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.checkerframework.checker.units.qual.C;
import org.opentripplanner.middleware.otp.graphql.PlanCoordinateInput;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.Objects;

/**
 * Helper class to contain lat/lon values.
 */
public class Coordinates {
    public Double lon;
    public Double lat;

    /** Required for JSON serialization */
    public Coordinates() {
    }

    public Coordinates(Double lat, Double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public Coordinates(TrackingLocation trackingLocation) {
        this.lat = trackingLocation.lat;
        this.lon = trackingLocation.lon;
    }

    public Coordinates(Position position) {
        this.lat = position.getLatitude();
        this.lon = position.getLongitude();
    }

    public Coordinates(Step step) {
        this.lat = step.lat;
        this.lon = step.lon;
    }

    public Coordinates(Place place) {
        this.lat = place.lat;
        this.lon = place.lon;
    }

    @JsonIgnore
    @BsonIgnore
    public String getCoordinates() {
        return lat + "," + lon;
    }

    @JsonIgnore
    @BsonIgnore
    public static Coordinates fromPlanCoordinateInput(PlanCoordinateInput coordinates) {
        return new Coordinates(coordinates.latitude, coordinates.longitude);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinates that = (Coordinates) o;
        return Objects.equals(lon, that.lon) && Objects.equals(lat, that.lat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lon, lat);
    }

    @Override
    public String toString() {
        return String.format("Coordinates{lat=%s, lon=%s}", lat, lon);
    }
}
