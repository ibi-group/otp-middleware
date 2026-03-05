package org.opentripplanner.middleware.otp.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegTest {
    @Test
    void canClone() throws CloneNotSupportedException {
        Leg leg = new Leg();
        leg.id = "123456abc";
        leg.from = new Place();
        leg.from.lon = -84.144;
        leg.from.lat = 33.863;
        leg.from.name = "Leg from place";
        leg.to = new Place();
        leg.to.lon = -84.398;
        leg.to.lat = 33.842;
        leg.to.name = "Leg to place";

        Leg clonedLeg = leg.clone();

        assertEquals(leg.id, clonedLeg.id);
        assertEquals(leg.from.name, clonedLeg.from.name);
        assertEquals(leg.from.lat, clonedLeg.from.lat);
        assertEquals(leg.from.lon, clonedLeg.from.lon);
        assertEquals(leg.to.name, clonedLeg.to.name);
        assertEquals(leg.to.lat, clonedLeg.to.lat);
        assertEquals(leg.to.lon, clonedLeg.to.lon);
    }
}
