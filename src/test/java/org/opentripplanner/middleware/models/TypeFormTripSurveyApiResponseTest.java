package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.models.TypeFormTripSurveyResponseTest.EXPECTED_CSV_ROW;
import static org.opentripplanner.middleware.models.TypeFormTripSurveyResponseTest.makeResponse;

class TypeFormTripSurveyApiResponseTest {
    @Test
    void toCsv() {
        TypeFormTripSurveyResponse response1 = makeResponse();
        TypeFormTripSurveyResponse response2 = makeResponse();
        TypeFormTripSurveyApiResponse apiResponse = new TypeFormTripSurveyApiResponse();
        apiResponse.items = List.of(response1, response2);

        String expectedHeader = "id,status,started,completed,notification_id,trip_id,user_id,field1,field2";

        assertEquals(String.format("%s%n%s%n%s%n", expectedHeader, EXPECTED_CSV_ROW, EXPECTED_CSV_ROW), apiResponse.toCsvRow());
    }
}
