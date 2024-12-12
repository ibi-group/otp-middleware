package org.opentripplanner.middleware.controllers.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripSurveyControllerTest {
    @Test
    void canMakeTripSurveyUrl() {
        assertEquals(
            "https://subdomain.typeform.com/to/survey-1#user_id=user-2&trip_id=trip-3&notification_id=notif-4",
            TripSurveyController.makeTripSurveyUrl("subdomain", "survey-1", "user-2", "trip-3", "notif-4")
        );
    }
}
