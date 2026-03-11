package org.opentripplanner.middleware.itinerarymatching;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.otp.response.Leg;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LegIdProcessorTest {

    @Test
    void canComputeLegIdForDate() {
        final String expectedId = "rO0ABXdaABhTQ0hFRFVMRURfVFJBTlNJVF9MRUdfVjMADk1BUlRBOjEwNzQzMjc4AAoyMDI1LTEyLTA1AAAAAgAAAA4AC01BUlRBOjY4MDU3AAtNQVJUQTo4MjAxNAAA";
        LocalDate desiredServiceDate = LocalDate.of(2025, Month.DECEMBER, 5);

        Leg leg = new Leg();
        leg.id = "rO0ABXdaABhTQ0hFRFVMRURfVFJBTlNJVF9MRUdfVjMADk1BUlRBOjEwNzQzMjc4AAoyMDI1LTEyLTAzAAAAAgAAAA4AC01BUlRBOjY4MDU3AAtNQVJUQTo4MjAxNAAA";
        // Dummy service date, different from the desired one above.
        leg.serviceDate = "20240420";

        assertNotEquals(expectedId, leg.id);
        assertNotEquals(desiredServiceDate.toString(), leg.serviceDate);

        String newLegId = LegIdProcessor.computeLegIdForServiceDate(leg, desiredServiceDate);
        assertEquals(expectedId, newLegId);
    }
}
