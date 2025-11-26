package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.LocalDateTime;
import java.util.List;

import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;

public class ItineraryMatchingUtils {
    public static Itinerary createTransitWalkTransitItinerary(LocalDateTime baseTime) {
        Itinerary itinerary = new Itinerary();
        itinerary.legs = List.of(
            createBusLeg("transit-leg-id-1", baseTime, baseTime.plusMinutes(10)),
            createWalkLeg(baseTime.plusMinutes(20), baseTime.plusMinutes(30)),
            createBusLeg("transit-leg-id-2", baseTime.plusMinutes(40), baseTime.plusMinutes(50))
        );
        return itinerary;
    }

    public static Leg createBusLeg(String id, LocalDateTime fromTime, LocalDateTime toTime) {
        Leg busLeg = new Leg();
        busLeg.transitLeg = true;
        busLeg.mode = "BUS";
        busLeg.id = id;
        busLeg.startTime = convertToDate(fromTime);
        busLeg.endTime = convertToDate(toTime);
        return busLeg;
    }

    public static Leg createWalkLeg(LocalDateTime fromTime, LocalDateTime toTime) {
        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";
        walkLeg.startTime = convertToDate(fromTime);
        walkLeg.endTime = convertToDate(toTime);
        return walkLeg;
    }
}
