package org.opentripplanner.middleware.utils.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "file",
    "lineNumber",
    "errorClass"
})
public class GroupingFields {

    public String file;
    public Integer lineNumber;
    public String errorClass;

    @Override
    public String toString() {
        return "GroupingFields{" +
            "file='" + file + '\'' +
            ", lineNumber=" + lineNumber +
            ", errorClass='" + errorClass + '\'' +
            '}';
    }
}