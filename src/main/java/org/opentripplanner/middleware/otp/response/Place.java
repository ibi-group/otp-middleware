package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

/**
 * Plan response, place information. Produced using http://www.jsonschema2pojo.org/
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Place implements Cloneable {

    public String name;
    public Double lon;
    public Double lat;
    public Date departure;
    public String orig;
    public String vertexType;
    public String stopId;
    public Date arrival;
    public Integer stopIndex;
    public Integer stopSequence;
    public String stopCode;
    public String platformCode;
    public String zoneId;
    public String bikeShareId;
    public Set<String> networks;
    public String address;

    protected Place clone() throws CloneNotSupportedException {
        return (Place) super.clone();
    }
}
