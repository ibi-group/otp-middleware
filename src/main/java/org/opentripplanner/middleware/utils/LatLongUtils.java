package org.opentripplanner.middleware.utils;

import java.util.Objects;
import java.util.Random;

/**
 * Utility class to randomize lat/long coordinates. Information on this can be found here:
 * https://gis.stackexchange.com/questions/25877/generating-random-locations-nearby. Original code (in Javascript) can
 * be found and tested here: http://jsfiddle.net/hoolymama/56wzdtax/.
 */
public class LatLongUtils {
    public static boolean IS_TEST = false;
    private static final long EARTH_RADIUS = 6371000;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double THREE_PI = Math.PI * 3;
    private static final double TWO_PI = Math.PI * 2;
    private static final int MINIMUM_DISTANCE_IN_METRES = 50;
    private static final int MAXIMUM_DISTANCE_IN_METRES = 100;

    public static final double TEST_LAT = 33.64070037704429;
    public static final double TEST_LON = -84.44622866991179;

    /**
     * Generate randomized lat/long values at a random distance and bearing from the original values.
     */
    public static Coordinates getRandomizedCoordinates(Coordinates coordinates) {
        if (IS_TEST) {
            // If testing, return a fixed location (Atlanta airport). This is to make sure the response is consistent
            // for comparing to snapshot.
            return new Coordinates(TEST_LAT, TEST_LON);
        }

        Random r = new Random();
        int distanceInMetres = r.nextInt(MAXIMUM_DISTANCE_IN_METRES - MINIMUM_DISTANCE_IN_METRES)
            + MINIMUM_DISTANCE_IN_METRES;
        double rnd = Math.random();
        double randomDistance = Math.pow(rnd, 0.5) * distanceInMetres;
        return pointAtDistance(coordinates, randomDistance);
    }

    /**
     * Create new lat/long coordinate at a random bearing at the predefined distance from the provided coordinates. If
     * the distance is 100m, the new coordinates will be 100m from the original coordinates at a random point between 0
     * and 365 degrees.
     */
    private static Coordinates pointAtDistance(Coordinates coordinates, double distance) {
        Coordinates coords = toRadians(coordinates);
        double sinLat = Math.sin(coords.latitude);
        double cosLat = Math.cos(coords.latitude);

	    double bearing = Math.random() * TWO_PI;
        double theta = distance / EARTH_RADIUS;
        double sinBearing = Math.sin(bearing);
        double cosBearing = Math.cos(bearing);
        double sinTheta = Math.sin(theta);
        double cosTheta = Math.cos(theta);

        double latitude = Math.asin(sinLat * cosTheta + cosLat * sinTheta * cosBearing);
        double longitude =
            coords.longitude +
            Math.atan2(sinBearing * sinTheta * cosLat, cosTheta - sinLat * Math.sin(latitude));
        longitude = ((longitude + THREE_PI) % TWO_PI) - Math.PI;
        return toDegrees(new Coordinates(latitude, longitude));
    }

    /**
     * Convert coordinates from degrees to radians.
     */
    private static Coordinates toRadians(Coordinates coordinates){
        return new Coordinates(coordinates.latitude * DEG_TO_RAD, coordinates.longitude * DEG_TO_RAD);
    }

    /**
     * Convert coordinates from radians to degrees.
     */
    private static Coordinates toDegrees(Coordinates coordinates){
        return new Coordinates(coordinates.latitude / DEG_TO_RAD, coordinates.longitude / DEG_TO_RAD);
    }

    /**
     * Helper class to contain lat/long values.
     */
    public static class Coordinates {
        public final double latitude;
        public final double longitude;

        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Coordinates that = (Coordinates) o;
            return Double.compare(that.latitude, latitude) == 0 && Double.compare(that.longitude, longitude) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(latitude, longitude);
        }
    }
}
