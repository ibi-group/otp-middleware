package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.stream.Collectors;

/** Data structure for TypeForm survey responses. Only including relevant fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeFormTripSurveyResponse {
    public String response_id;

    public String response_type;

    public String landed_at;

    public String submitted_at;

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

        public String toCsvContent() {
            if ("choices".equals(type) && choices != null && choices.labels != null) {
                return String.join(";", choices.labels);
            } else if ("choice".equals(type) && choice != null && choice.label != null) {
                return choice.label;
            }
            return "";
        }
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
        // id, type/state, landed, submitted, hidden fields, textual responses in order they appear.
        return String.join(
            ",",
            response_id,
            response_type,
            landed_at,
            submitted_at,
            hidden.notification_id,
            hidden.trip_id,
            hidden.user_id,
            answers.stream().map(Answer::toCsvContent).collect(Collectors.joining(","))
        );
    }
}

