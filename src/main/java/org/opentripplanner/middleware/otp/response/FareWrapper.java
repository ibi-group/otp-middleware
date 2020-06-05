package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "fare",
    "details"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareWrapper {

    @JsonProperty("fare")
    public Fare fare;
    @JsonProperty("details")
    public FareDetails details;
}
