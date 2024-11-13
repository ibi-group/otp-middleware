package org.opentripplanner.middleware.models.typeform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Relevant fields info in surveys. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Field {
    public String id;

    public String title;

    public Field() {
        // Empty constructor for deserialization
    }

    public Field(String id) {
        this.id = id;
    }

    public Field(String id, String title) {
        this.id = id;
        this.title = title;
    }
}
