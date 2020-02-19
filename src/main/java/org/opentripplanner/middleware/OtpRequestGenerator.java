package org.opentripplanner.middleware;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OtpRequestGenerator {

    final static String OTP_PLAN_URL = "https://fdot-otp-server.ibi-transit.com/otp/routers/default/plan";
    final static String OTP_UI_URL = "https://fdot-otp.ibi-transit.com/#/";
    final static int NUM_ITINERARIES = 3;

    public static void main(String[] args) {
        // Just print out a list of OTP requests from the places in the const file.
        // Combine the following modes: Walk, Transit, Bike, Bikeshare

        String modeParam = "WALK%2CBUS%2CRAIL"; // Walk, Bus, Rail

        System.out.println("<!DOCTYPE html><html><head>");
        System.out.println("<link rel='stylesheet' href='request-generator.css' />");
        System.out.println("</head>");
        System.out.println("<body>");
        System.out.println("<h1>Origin/Destination combinations</h1>");
        System.out.println("<table>");

        System.out.println("<tr class='header'><td>From \\ To</td>");

        List<Map.Entry<String, Double[]>> entries = Const.locations.entrySet().stream().collect(Collectors.toList());

        for (int i = 0; i < entries.size(); i++) {
            System.out.println("<td><span>" + entries.get(i).getKey() + "</span></td>");
        }
        System.out.println("</tr>");

        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double[]> e1 = entries.get(i);
            System.out.println("<tr><td>" + e1.getKey() + "</td>");
            for (int j = 0; j < entries.size(); j++) {
                if (i == j) {
                    System.out.println("<td></td>");
                }
                else {
                    Map.Entry<String, Double[]> e2 = entries.get(j);

                    String uiUrl = makeOtpRequestUrl(OTP_UI_URL, e1, e2, modeParam);
                    System.out.println("<td><a target='_blank' href='" + uiUrl + "'>Go</a>");

                    // System.out.println(makeOtpRequestUrl(OTP_PLAN_URL, e1, e2, modeParam));
                    count++;
                }
            }
        }

        System.out.println("</table>");
        System.out.println("<p>Total combinations: " + count + "</p>");

        System.out.println("<h2>List of places</h2><ol>");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double[]> e1 = entries.get(i);
            System.out.println("<li>" + e1.toString() + "</li>");
        }
        System.out.println("</ol>");

        System.out.println("</body><html>");
    }

    public static String getPlaceParam(Map.Entry<String, Double[]> loc) {
        Double[] arr = loc.getValue();
        double lat = arr[1];
        double lon = arr[0];

        return URLEncoder.encode(loc.getKey(), StandardCharsets.UTF_8) + "::" + lat + "," + lon;
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
