package org.opentripplanner.middleware.utils;

import org.locationtech.jts.geom.LineSegment;

public class GeometryUtils {

    private GeometryUtils() {
    }

    public static final double RADIUS_OF_EARTH_IN_KM = 6371.01;
    public static final double RADIUS_OF_EARTH_IN_M = RADIUS_OF_EARTH_IN_KM * 1000;

    /**
     * Get the distance in meters between two lat/lon points.
     */
    public static double getDistance(Coordinates start, Coordinates end) {
        double[] startXY = convertLatLonToXY(start.lat, start.lon);
        double[] endXY = convertLatLonToXY(end.lat, end.lon);
        var point1 = new org.locationtech.jts.geom.Coordinate(startXY[0], startXY[1]);
        var point2 = new org.locationtech.jts.geom.Coordinate(endXY[0], endXY[1]);
        return point1.distance(point2);
    }

    /**
     * Get the distance between a line and point.
     */
    public static double getDistanceFromLine(Coordinates start, Coordinates end, Coordinates traveler) {
        double[] startXY = convertLatLonToXY(start.lat, start.lon);
        double[] endXY = convertLatLonToXY(end.lat, end.lon);
        double[] travelerXY = convertLatLonToXY(traveler.lat, traveler.lon);
        LineSegment ls = new LineSegment(startXY[0], startXY[1], endXY[0], endXY[1]);
        return ls.distance(new org.locationtech.jts.geom.Coordinate(travelerXY[0], travelerXY[1]));
    }

    /**
     * Calculate x and y using Mercator projection.
     */
    private static double[] convertLatLonToXY(double latitude, double longitude) {
        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);
        double earthRadius = RADIUS_OF_EARTH_IN_M;
        double x = earthRadius * lonRad;
        double y = earthRadius * Math.log(Math.tan(Math.PI / 4 + latRad / 2));
        return new double[] {x, y};
    }


    /**
     * Calculate the bearing between two coordinates.
     */
    public static double calculateBearing(Coordinates start, Coordinates destination) {
        double deltaLon = destination.lon - start.lon;
        double y = Math.sin(Math.toRadians(deltaLon)) * Math.cos(Math.toRadians(destination.lat));
        double x = Math.cos(Math.toRadians(start.lat)) * Math.sin(Math.toRadians(destination.lat)) -
            Math.sin(Math.toRadians(start.lat)) * Math.cos(Math.toRadians(destination.lat)) *
                Math.cos(Math.toRadians(deltaLon));

        double initialBearing = Math.atan2(y, x);
        initialBearing = Math.toDegrees(initialBearing);
        initialBearing = (initialBearing + 360) % 360; // Normalize to range [0, 360)

        return initialBearing;
    }

    /**
     * Is the point between the start and end coordinates.
     */
    public static boolean isPointBetween(Coordinates start, Coordinates end, Coordinates location) {
        double startToLocationDistance = getDistance(start, location);
        double startToEndDistance = getDistance(start, end);
        double endToLocationDistance = getDistance(end, location);
        double totalDistanceViaLocation = startToLocationDistance + endToLocationDistance;

        // Check if the sum of distances from start to point and end to point
        // is approximately equal to the distance from start to end.
        return Math.abs(totalDistanceViaLocation - startToEndDistance) < 1;
    }
}
