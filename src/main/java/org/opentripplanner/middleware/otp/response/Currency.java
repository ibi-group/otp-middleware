package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "symbol",
    "currency",
    "defaultFractionDigits",
    "currencyCode"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Currency {

    @JsonProperty("symbol")
    public String symbol;
    @JsonProperty("currency")
    public String currency;
    @JsonProperty("defaultFractionDigits")
    public Integer defaultFractionDigits;
    @JsonProperty("currencyCode")
    public String currencyCode;
}
