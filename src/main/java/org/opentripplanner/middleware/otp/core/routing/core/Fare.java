package org.opentripplanner.middleware.otp.core.routing.core;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.*;

/**
 * <p>
 * Fare is a set of fares for different classes of users.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fare {

    public static enum FareType implements Serializable {
        regular, student, senior, tram, special, youth
    }

    /**
     * A mapping from {@link FareType} to {@link Money}.
     */
    public HashMap<String, Money> fare;

    /**
     * A mapping from {@link FareType} to a list of {@link FareComponent}.
     * The FareComponents are stored in an array instead of a list because JAXB doesn't know how to deal with
     * interfaces when serializing a trip planning response, and List is an interface.
     * See https://stackoverflow.com/a/1119241/778449
     */
//    public HashMap<String, FareComponent[]> details;

    public Fare() {
        fare = new HashMap<>();
//        details = new HashMap<>();
    }

    public String toString() {
        StringBuilder buffer = new StringBuilder("Fare(");
        for (String fareTypeName : fare.keySet()) {
            FareType original = FareType.valueOf(fareTypeName);
            Money cost = fare.get(fareTypeName);
            buffer.append("[");
            buffer.append(original.toString());
            buffer.append(":");
            buffer.append(cost.toString());
            buffer.append("], ");
        }
        buffer.append(")");

        return buffer.toString();
    }

    /** MongoDB can only work with HashMaps with String keys. This prevents the original type of FareType being used.
    * This override forces deserialization to use the String value of the FareType enum */
    public void setFare(HashMap<String, Money> fare) {
        this.fare = fare;
    }
}
