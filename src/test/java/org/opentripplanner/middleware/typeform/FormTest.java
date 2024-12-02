package org.opentripplanner.middleware.typeform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormTest {
    @Test
    void toCsvHeader() {
        Form form = new Form();
        form.hidden = List.of("user_id", "notification_id");
        form.fields = List.of(
            new Field("field-1", "Hi, what is your name?"), // Questions can include commas
            new Field("field-2", "Where do you live?")
        );

        assertEquals(
            "id,status,started,completed,user_id,notification_id,\"Hi, what is your name?\",\"Where do you live?\"",
            form.toCsvHeader()
        );
    }
}
