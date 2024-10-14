package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stop implements Cloneable {
    public List<LocalizedAlert> alerts = new ArrayList<>();
    public String code;
    public String gtfsId;
    public String id;
    public Double lon;
    public Double lat;

    /**
     * Clone this object.
     */
    protected Place clone() throws CloneNotSupportedException {
        return (Place) super.clone();
    }
}
