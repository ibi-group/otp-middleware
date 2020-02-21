package org.opentripplanner.middleware;

import java.util.Map;

import static org.opentripplanner.middleware.Const.*;

public class OtpRequestGenerator {

    public static void main(String[] args) {
        // Just print out a list of OTP requests from the places in the const file.
        // Combine the following modes: Walk, Transit, Bike, Bikeshare

        System.out.println("<!DOCTYPE html><html><head>");
        System.out.println("<link rel='stylesheet' href='request-generator.css' />");
        System.out.println("</head>");
        System.out.println("<body>");


        System.out.println("<h1>Origin/Destination combinations</h1>");
        System.out.println("<table>");
        System.out.println("<tr class='header'><td>From \\ To</td>");

        for (int i = 0; i < locations.size(); i++) {
            System.out.println("<td><span>" + locations.get(i).getKey() + "</span></td>");
        }
        System.out.println("</tr>");

        int count = 0;
        for (int i = 0; i < locations.size(); i++) {
            Map.Entry<String, Double[]> e1 = locations.get(i);
            System.out.println("<tr><td>" + e1.getKey() + "</td>");
            for (int j = 0; j < locations.size(); j++) {
                if (i == j) {
                    System.out.println("<td></td>");
                }
                else {
                    Map.Entry<String, Double[]> e2 = locations.get(j);

                    System.out.println("<td>");

                    for (int k = 0; k < modeParams.size(); k++) {
                        Map.Entry<String, String> m = modeParams.get(k);
                        String uiUrl = Util.makeOtpRequestUrl(OTP_UI_URL, e1, e2, m.getValue());
                        System.out.println("<a target='_blank' href='" + uiUrl + "'>" + m.getKey() + "</a> ");
                    };

                    System.out.println("</td>");
                    count++;
                }
            }
        }

        System.out.println("</table>");
        System.out.println("<p>Total combinations: " + count + "</p>");
        System.out.println("</body><html>");
    }
}
