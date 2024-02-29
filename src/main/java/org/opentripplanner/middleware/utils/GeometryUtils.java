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
}
