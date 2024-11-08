package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeFormTripSurveyResponseTest {

    public static final String EXPECTED_CSV_ROW = "response-id-0,completed,2024-10-25T15:37:42Z,2024-10-25T15:46:27Z,notification-id-1,trip-id-2,user-id-3,Field1 choice,Field2 ChoiceA;Field2 ChoiceB";

    @Test
    void toCsvRow() {
        TypeFormTripSurveyResponse response = makeResponse();
        assertEquals(EXPECTED_CSV_ROW, response.toCsvRow());
    }

    public static TypeFormTripSurveyResponse makeResponse() {
        TypeFormTripSurveyResponse response = new TypeFormTripSurveyResponse();
        response.landed_at = "2024-10-25T15:37:42Z";
        response.submitted_at = "2024-10-25T15:46:27Z";
        response.response_id = "response-id-0";
        response.response_type = "completed";
        response.hidden = new TypeFormTripSurveyResponse.Hidden();
        response.hidden.notification_id = "notification-id-1";
        response.hidden.trip_id = "trip-id-2";
        response.hidden.user_id = "user-id-3";

        TypeFormTripSurveyResponse.Answer answer1 = new TypeFormTripSurveyResponse.Answer();
        answer1.type = "choice";
        answer1.choice = new TypeFormTripSurveyResponse.Choice();
        answer1.choice.label = "Field1 choice";
        answer1.field = new TypeFormTripSurveyResponse.Field();
        answer1.field.id = "field-id-1";

        TypeFormTripSurveyResponse.Answer answer2 = new TypeFormTripSurveyResponse.Answer();
        answer2.type = "choices";
        answer2.choices = new TypeFormTripSurveyResponse.Choices();
        answer2.choices.labels = List.of("Field2 ChoiceA", "Field2 ChoiceB");
        answer2.field = new TypeFormTripSurveyResponse.Field();
        answer2.field.id = "field-id-2";

        response.answers = List.of(answer1, answer2);
        return response;
    }
}
