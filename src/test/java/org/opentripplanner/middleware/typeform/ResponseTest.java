package org.opentripplanner.middleware.typeform;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResponseTest {

    public static final String EXPECTED_CSV_BEGINNING = "response-id-0,completed,2024-10-25T15:37:42Z,2024-10-25T15:46:27Z,notification-id-1,trip-id-2,user-id-3,";
    public static final String EXPECTED_CSV_ENDING = "\"Field1 choice\",\"Field2, ChoiceA;Field2, ChoiceB\",\"Field3 answer\"";
    public static final String EXPECTED_CSV_ROW = EXPECTED_CSV_BEGINNING + EXPECTED_CSV_ENDING;

    @ParameterizedTest
    @MethodSource("createToCsvRowCases")
    void toCsvRow(Response response, String expected) {
        assertEquals(expected, response.toCsvRow());
    }

    private static Stream<Arguments> createToCsvRowCases() {
        Response responseWithoutAnswers = makeResponse();
        responseWithoutAnswers.answers = null;
        return Stream.of(
            Arguments.of(makeResponse(), EXPECTED_CSV_ROW),
            Arguments.of(responseWithoutAnswers, EXPECTED_CSV_BEGINNING)
        );
    }

    public static Response makeResponse() {
        Response response = new Response();
        response.landed_at = "2024-10-25T15:37:42Z";
        response.submitted_at = "2024-10-25T15:46:27Z";
        response.response_id = "response-id-0";
        response.response_type = "completed";
        response.hidden = new Response.Hidden();
        response.hidden.notification_id = "notification-id-1";
        response.hidden.trip_id = "trip-id-2";
        response.hidden.user_id = "user-id-3";

        Response.Answer answer1 = new Response.Answer();
        answer1.type = "choice";
        answer1.choice = new Response.Choice();
        answer1.choice.label = "Field1 choice";
        answer1.field = new Field("field-id-1");

        Response.Answer answer2 = new Response.Answer();
        answer2.type = "choices";
        answer2.choices = new Response.Choices();
        answer2.choices.labels = List.of("Field2, ChoiceA", "Field2, ChoiceB"); // Answers can include commas
        answer2.field = new Field("field-id-2");

        Response.Answer answer3 = new Response.Answer();
        answer3.type = "text";
        answer3.text = "Field3 answer";
        answer3.field = new Field("field-id-3");

        response.answers = List.of(answer1, answer2, answer3);
        return response;
    }
}
