package org.opentripplanner.middleware.utils;

import java.util.Random;

/**
 * Utility class to randomize lat/long coordinates. Information on this can be found here:
 * https://gis.stackexchange.com/questions/25877/generating-random-locations-nearby. Original code (in Javascript) can
 * be found and tested here: http://jsfiddle.net/hoolymama/56wzdtax/.
 */
public class LatLongUtils {
    private static final long EARTH_RADIUS = 6371000;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double THREE_PI = Math.PI * 3;
    private static final double TWO_PI = Math.PI * 2;
    private static final int MINIMUM_DISTANCE_IN_METRES = 50;
    private static final int MAXIMUM_DISTANCE_IN_METRES = 100;

    public static final double TEST_LAT = 33.64070037704429;
    public static final double TEST_LON = -84.44622866991179;

    public static Coordinates getRandomizedCoordinates(Coordinates coordinates) {
        return getRandomizedCoordinates(coordinates, false);
    }

    /**
     * Generate randomized lat/long values at a random distance and bearing from the original values. If in test mode,
     * use fixed test coordinates.
     */
    public static Coordinates getRandomizedCoordinates(Coordinates coordinates, boolean isTest) {
        if (isTest) {
            // If testing, return a fixed location (Atlanta airport). This is to make sure the response is consistent
            // for comparing to snapshots.
            return new Coordinates(TEST_LAT, TEST_LON);
        }

        Random random = new Random();
        int distanceInMetres = random.nextInt(MAXIMUM_DISTANCE_IN_METRES - MINIMUM_DISTANCE_IN_METRES)
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
        double sinLat = Math.sin(coords.lat);
        double cosLat = Math.cos(coords.lat);

        double bearing = Math.random() * TWO_PI;
        double theta = distance / EARTH_RADIUS;
        double sinBearing = Math.sin(bearing);
        double cosBearing = Math.cos(bearing);
        double sinTheta = Math.sin(theta);
        double cosTheta = Math.cos(theta);

        double latitude = Math.asin(sinLat * cosTheta + cosLat * sinTheta * cosBearing);
        double longitude =
            coords.lon +
                Math.atan2(sinBearing * sinTheta * cosLat, cosTheta - sinLat * Math.sin(latitude));
        longitude = ((longitude + THREE_PI) % TWO_PI) - Math.PI;
        return toDegrees(new Coordinates(latitude, longitude));
    }

    /**
     * Convert coordinates from degrees to radians.
     */
    private static Coordinates toRadians(Coordinates coordinates) {
        return new Coordinates(coordinates.lat * DEG_TO_RAD, coordinates.lon * DEG_TO_RAD);
    }

    /**
     * Convert coordinates from radians to degrees.
     */
    private static Coordinates toDegrees(Coordinates coordinates) {
        return new Coordinates(coordinates.lat / DEG_TO_RAD, coordinates.lon / DEG_TO_RAD);
    }

}
