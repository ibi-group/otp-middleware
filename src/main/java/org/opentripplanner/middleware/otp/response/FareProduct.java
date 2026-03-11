package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareProduct implements Cloneable {
    public String id;
    public FareMedium medium;
    public String name;
    public RiderCategory riderCategory;
    public Money price;
    public List<FareDependency> dependencies;
}
