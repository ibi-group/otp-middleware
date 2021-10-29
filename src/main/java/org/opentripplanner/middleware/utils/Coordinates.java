package org.opentripplanner.middleware.utils;

import java.util.Objects;

/**
 * Helper class to contain lat/lon values.
 */
public class Coordinates {
    public double lat;
    public double lon;

    /** Required for JSON serialization */
    public Coordinates() {
    }

    public Coordinates(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinates that = (Coordinates) o;
        return Double.compare(that.lat, lat) == 0 && Double.compare(that.lon, lon) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }
}
