package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class ItineraryRootDeserializer extends JsonDeserializer<Itinerary> {
    @Override
    public Itinerary deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // Ignore the root object
        if (p.currentToken() == JsonToken.START_OBJECT) {
            p.nextToken();
        }
        p.nextToken();

        // Parse the itinerary normally
        Itinerary child = ctxt.readValue(p, Itinerary.class);

        if (p.currentToken() == JsonToken.END_OBJECT) {
            p.nextToken();
        }
        return child;
    }
}
