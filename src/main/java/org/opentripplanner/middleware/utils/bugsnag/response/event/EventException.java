package org.opentripplanner.middleware.utils.bugsnag.response.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "errorClass",
    "message"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventException {

    private String errorClass;
    private String message;
}
