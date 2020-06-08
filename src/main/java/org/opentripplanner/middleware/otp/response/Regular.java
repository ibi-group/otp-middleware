package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Plan response, regular price and route information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "fareId",
    "price",
    "routes"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Regular {

    @JsonProperty("fareId")
    public String fareId;
    @JsonProperty("price")
    public Price price;
    @JsonProperty("routes")
    public List<String> routes = null;

}
