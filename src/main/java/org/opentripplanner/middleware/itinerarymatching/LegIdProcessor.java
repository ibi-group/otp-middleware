package org.opentripplanner.middleware.itinerarymatching;

import org.opentripplanner.middleware.otp.response.Leg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Helper class for generating leg ids.
 */
public class LegIdProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(LegIdProcessor.class);

    private LegIdProcessor() {
        // Hides the default public constructor
    }

    /**
     * Computes the leg id for a given leg and service date.
     */
    public static String computeLegIdForServiceDate(Leg leg, LocalDate desiredServiceDate) {
        byte[] serializedLegReference;
        try {
            serializedLegReference = Base64.getUrlDecoder().decode(leg.id);
        } catch (IllegalArgumentException e) {
            LOG.info("Unable to decode leg reference (invalid base64 encoding): '{}'", leg.id, e);
            return null;
        }

        var input = new ByteArrayInputStream(serializedLegReference);
        String type = null;
        String tripId = null;
        int stopPosition1 = Integer.MIN_VALUE;
        int stopPosition2 = Integer.MIN_VALUE;
        String stopId1 = null;
        String stopId2 = null;
        try (var in = new ObjectInputStream(input)) {
            // Follow the order in which OTP encoded this order must be the same in the encode and decode function

            type = in.readUTF();
            tripId = in.readUTF();
            in.readUTF(); // Consume service date value, but ignore.
            stopPosition1 = in.readInt();
            stopPosition2 = in.readInt();
            stopId1 = in.readUTF();
            stopId2 = in.readUTF();
        } catch (IOException e) {
            LOG.warn(
                "Unable to decode leg reference (incompatible serialization format): '{}'",
                leg.id,
                e
            );
        }

        try (
            var buf = new ByteArrayOutputStream();
            var out = new ObjectOutputStream(buf)
        ) {
            out.writeUTF(type);
            out.writeUTF(tripId);
            out.writeUTF(desiredServiceDate.toString());
            out.writeInt(stopPosition1);
            out.writeInt(stopPosition2);
            out.writeUTF(stopId1);
            out.writeUTF(stopId2);
            out.writeUTF("");
            out.flush();
            return Base64.getUrlEncoder().encodeToString(buf.toByteArray());
        } catch (IOException e) {
            LOG.error("Failed to encode leg reference", e);
            return null;
        }
    }
}
