package org.opentripplanner.middleware.itinerarymatching;

import jersey.repackaged.com.google.common.collect.Lists;
import org.opentripplanner.middleware.otp.response.Agency;
import org.opentripplanner.middleware.otp.response.EncodedPolyline;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Route;

import java.time.LocalDateTime;
import java.util.List;

import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToDate;

public class ItineraryMatchingUtils {
    public static Itinerary createTransitWalkTransitItinerary(LocalDateTime baseTime) {
        Itinerary itinerary = new Itinerary();
        List<Leg> legs = Lists.newArrayList(
            createBusLeg1(baseTime, baseTime.plusMinutes(10)),
            createWalkLeg(baseTime.plusMinutes(20), baseTime.plusMinutes(30)),
            createBusLeg2(baseTime.plusMinutes(40), baseTime.plusMinutes(50))
        );
        itinerary.legs = legs;
        legs.get(1).from = legs.get(0).to;
        legs.get(1).to = legs.get(2).from;

        return itinerary;
    }

    public static Itinerary createWalkTransitWalkTransitWalkItinerary(LocalDateTime baseTime) {
        Itinerary itinerary = new Itinerary();
        List<Leg> legs = Lists.newArrayList(
            createWalkLeg(baseTime.minusMinutes(10), baseTime.minusMinutes(5)),
            createBusLeg1(baseTime, baseTime.plusMinutes(10)),
            createWalkLeg(baseTime.plusMinutes(20), baseTime.plusMinutes(30)),
            createBusLeg2(baseTime.plusMinutes(40), baseTime.plusMinutes(50)),
            createWalkLeg(baseTime.plusMinutes(50), baseTime.plusMinutes(55))
        );
        itinerary.legs = legs;
        legs.get(0).from = legs.get(0).to = itinerary.legs.get(1).from;
        legs.get(2).from = legs.get(1).to;
        legs.get(2).to = legs.get(3).from;
        legs.get(4).from = legs.get(4).to = itinerary.legs.get(3).to;
        return itinerary;
    }

    public static Leg createBusLeg(String id, LocalDateTime fromTime, LocalDateTime toTime) {
        Leg busLeg = new Leg();
        busLeg.transitLeg = true;
        busLeg.mode = "BUS";
        busLeg.id = id;
        busLeg.startTime = convertToDate(fromTime);
        busLeg.endTime = convertToDate(toTime);
        busLeg.steps = List.of();
        busLeg.legGeometry = new EncodedPolyline();
        busLeg.interlineWithPreviousLeg = false;
        busLeg.departureDelay = 0;
        busLeg.arrivalDelay = 0;

        Agency agency = new Agency();
        agency.name = "Agency";
        busLeg.agency = agency;

        busLeg.route = new Route();

        return busLeg;
    }

    public static Leg createBusLeg1(LocalDateTime fromTime, LocalDateTime toTime) {
        Leg busLeg = createBusLeg("transit-leg-id-1", fromTime, toTime);

        Place stop1 = new Place();
        stop1.lat = 1.0;
        stop1.lon = 2.0;

        Place stop2 = new Place();
        stop2.lat = 3.0;
        stop2.lon = 4.0;

        busLeg.from = stop1;
        busLeg.to = stop2;

        busLeg.route.shortName = "10";

        return busLeg;
    }

    public static Leg createBusLeg2(LocalDateTime fromTime, LocalDateTime toTime) {
        Leg busLeg = createBusLeg("transit-leg-id-2", fromTime, toTime);

        Place stop1 = new Place();
        stop1.lat = 5.0;
        stop1.lon = 6.0;

        Place stop2 = new Place();
        stop2.lat = 7.0;
        stop2.lon = 8.0;

        busLeg.from = stop1;
        busLeg.to = stop2;

        busLeg.route.shortName = "20";

        return busLeg;
    }

    public static Leg createWalkLeg(LocalDateTime fromTime, LocalDateTime toTime) {
        Leg walkLeg = new Leg();
        walkLeg.mode = "WALK";
        walkLeg.startTime = convertToDate(fromTime);
        walkLeg.endTime = convertToDate(toTime);
        walkLeg.steps = List.of();
        walkLeg.legGeometry = new EncodedPolyline();
        return walkLeg;
    }
}
