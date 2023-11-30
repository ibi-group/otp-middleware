package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collection;

/**
 * Mobility profile data
 */
public class MobilityProfile implements Serializable {
    public enum VisionLimitation {
        @JsonProperty("legally blind") LEGALLY_BLIND,
        @JsonProperty("low-vision") LOW_VISION,
        @JsonProperty("none") NONE
    }

    /** Whether the user indicates that their mobility is limited (slower). */
    public boolean isMobilityLimited;

    /** User may indicate zero or more mobility devices. */
    public Collection<String> mobilityDevices;

    /** Compound keyword that controller calculates from mobility and vision values. */
    @JsonIgnore
    public String mobilityMode;

    /** User may indicate levels of vision limitation. */
    public VisionLimitation visionLimitation;
}
