package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.List;

/** Data structure for TypeForm survey responses. Only including relevant fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeFormTripSurveyResponse {
    public String response_type;

    public Date landed_at;

    public Date submitted_at;

    public Hidden hidden;

    public List<Answer> answers;

    /** Relevant hidden fields in surveys. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hidden {
        public String notification_id;

        public String user_id;

        public String trip_id;
    }

    /** Relevant fields info in surveys. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Field {
        public String id;
    }

    /** Relevant answer fields in surveys. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Answer {
        public Field field;

        public String type;

        public Choice choice;

        public Choices choices;
    }

    /** Relevant choice fields in surveys. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        public String label;
    }

    /** Relevant multiple choice fields in surveys. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choices {
        public List<String> labels;
    }

    public String toCsvRow() {
        return "";
    }
}

