package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Data structure for TypeForm survey API response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeFormTripSurveyApiResponse {
    public List<TypeFormTripSurveyResponse> items;

    public String toCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append("id,status,started,completed,notification_id,trip_id,user_id,field1,field2");
        builder.append(System.lineSeparator());
        if (items != null) {
            for (TypeFormTripSurveyResponse response : items) {
                builder.append(response.toCsvRow());
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}