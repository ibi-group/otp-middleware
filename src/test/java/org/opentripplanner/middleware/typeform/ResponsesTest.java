package org.opentripplanner.middleware.typeform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.typeform.ResponseTest.EXPECTED_CSV_ROW;
import static org.opentripplanner.middleware.typeform.ResponseTest.makeResponse;

public class ResponsesTest {

    public static final String EXPECTED_HEADER = "id,status,started,completed,notification_id,trip_id,user_id,field1,field2";

    @Test
    void toCsv() {
        Response response1 = makeResponse();
        Response response2 = makeResponse();
        Responses apiResponse = new Responses();
        apiResponse.items = List.of(response1, response2);

        assertEquals(getExpectedCsv(), apiResponse.toCsv(EXPECTED_HEADER));
    }

    public static String getExpectedCsv() {
        return String.format("%s%n%s%n%s%n", EXPECTED_HEADER, EXPECTED_CSV_ROW, EXPECTED_CSV_ROW);
    }
}
