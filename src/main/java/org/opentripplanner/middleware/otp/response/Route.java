package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Route {
    public List<LocalizedAlert> alerts = new ArrayList<>();
    public String color;
    public String gtfsId;
    public String id;
    public String longName;
    public String shortName;
    public String textColor;
    public Integer type;
}
