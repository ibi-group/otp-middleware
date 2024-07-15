package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Device {
    @JsonProperty("DeviceName")
    public String deviceName;

    public String getDeviceName() {
        return deviceName;
    }
}
