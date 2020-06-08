package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Plan response, currency and cent information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "currency",
    "cents"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Price {

    @JsonProperty("currency")
    public Currency currency;
    @JsonProperty("cents")
    public Integer cents;

}
