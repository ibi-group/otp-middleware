package org.opentripplanner.middleware.bugsnag.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "name",
    "email"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Creator {

    public String id;
    public String name;
    public String email;

    @Override
    public String toString() {
        return "Creator{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", email='" + email + '\'' +
            '}';
    }
}
