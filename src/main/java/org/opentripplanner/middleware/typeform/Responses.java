package org.opentripplanner.middleware.typeform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Data structure for TypeForm survey API response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Responses {
    public List<Response> items;

    /** Populated in tests only */
    public boolean isTest;

    public String toCsv(String headers) {
        StringBuilder builder = new StringBuilder();
        builder.append(headers);
        builder.append(System.lineSeparator());
        if (items != null) {
            for (Response response : items) {
                builder.append(response.toCsvRow());
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}