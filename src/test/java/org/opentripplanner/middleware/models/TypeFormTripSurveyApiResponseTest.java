package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.models.TypeFormTripSurveyResponseTest.EXPECTED_CSV_ROW;
import static org.opentripplanner.middleware.models.TypeFormTripSurveyResponseTest.makeResponse;

public class TypeFormTripSurveyApiResponseTest {

    public static final String EXPECTED_HEADER = "id,status,started,completed,notification_id,trip_id,user_id,field1,field2";

    @Test
    void toCsv() {
        TypeFormTripSurveyResponse response1 = makeResponse();
        TypeFormTripSurveyResponse response2 = makeResponse();
        TypeFormTripSurveyApiResponse apiResponse = new TypeFormTripSurveyApiResponse();
        apiResponse.items = List.of(response1, response2);

        assertEquals(getExpectedCsv(), apiResponse.toCsv());
    }

    public static String getExpectedCsv() {
        return String.format("%s%n%s%n%s%n", EXPECTED_HEADER, EXPECTED_CSV_ROW, EXPECTED_CSV_ROW);
    }
}
