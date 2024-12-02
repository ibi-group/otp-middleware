package org.opentripplanner.middleware.typeform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Data structure for relevant TypeForm Form data. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Form {

    public List<Field> fields;

    public List<String> hidden;

    public String toCsvHeader() {
        List<String> standardHeaders = List.of("id", "status", "started", "completed");
        List<String> allFields = new ArrayList<>();
        allFields.addAll(standardHeaders);
        allFields.addAll(hidden);
        allFields.addAll(fields.stream().map(f -> String.format("\"%s\"", f.title)).collect(Collectors.toList()));

        return String.join(",", allFields);
    }
}
