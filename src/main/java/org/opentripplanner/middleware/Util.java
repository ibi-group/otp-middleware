package org.opentripplanner.middleware;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Util {
    final static int NUM_ITINERARIES = 3;

    public static String getPlaceParam(Map.Entry<String, Double[]> loc) {
        Double[] arr = loc.getValue();
        double lat = arr[1];
        double lon = arr[0];

        return URLEncoder.encode(loc.getKey() + "::" + lat + "," + lon, StandardCharsets.UTF_8);
    }

    public static String makeOtpRequestUrl(String baseUrl, Map.Entry<String, Double[]> loc1, Map.Entry<String, Double[]> loc2, String modeParam) {
        return baseUrl
                + "?fromPlace=" + getPlaceParam(loc1)
                + "&toPlace=" + getPlaceParam(loc2)
                + "&mode=" + modeParam
                + "&showIntermediateStops=true&maxWalkDistance=1609&optimize=QUICK&walkSpeed=1.34&ignoreRealtimeUpdates=true"
                + "&numItineraries=" + NUM_ITINERARIES;
    }
}
