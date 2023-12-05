package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Mobility profile data, to keeps track of values from UI or mobile app and
 * uses them to maintain a "mobility mode," which are keywords specified in
 * the Georgia Tech Mobility Profile Configuration / Logical Flow document.
 * <p>
 * Provided as part of {@link OtpUser}, example JSON format:
 * <code>
 *   ...
 *   "mobilityProfile": {
 *     "isMobilityLimited": true,
 *     "mobilityDevices": ["service animal", "electric wheelchair"],
 *     "visionLimitation": "low-vision",
 *     "mobilityMode": "WChairE-LowVision",
 *   }
 *   ...
 * </code>
 */
public class MobilityProfile implements Serializable {
    // Selected mobility mode keywords from Georgia Tech document.
    private static final Set<String> MOBILITY_DEVICES = Set.of("Device", "MScooter", "WChairE", "WChairM", "Some");

    public enum VisionLimitation {
        @JsonProperty("legally-blind") LEGALLY_BLIND,
        @JsonProperty("low-vision") LOW_VISION,
        @JsonProperty("none") NONE
    }

    /** Whether the user indicates that their mobility is limited (slower). */
    public boolean isMobilityLimited;

    /** User may indicate zero or more mobility devices. */
    public Collection<String> mobilityDevices = Collections.EMPTY_LIST;

    /** Compound keyword that controller calculates from mobility and vision values. */
    @JsonIgnore
    public String mobilityMode;

    /** User may indicate levels of vision limitation. */
    public VisionLimitation visionLimitation = VisionLimitation.NONE;


    /**
     * Construct the mobility mode keyword or compound keyword from fields in
     * a mobility profile, and update the mobility profile with it.  Follows
     * the Georgia Tech Mobility Profile Configuration / Logical Flow document,
     * so that the mode is constructed based on specific strings in a specific
     * order.  The device strings are expected to change on occasion.
     * @param mobilityProfile consulted to construct and update mobility mode
     */
    public static void updateMobilityMode(MobilityProfile mobilityProfile) {
        // Variable names and the strings we parse are from Georgia Tech document, to facilitate syncing
        // changes.  The testing for devices and vision in this order are from the same document; note
        // that this means the devices tested for later will override the earlier "Temp"orary settings.
        String mModeTemp = "None";
        String visionTemp = "None";

        // If "none" has been specified at all, we just wipe the mobility devices clear,
        // else we look at the mobility devices and settle on the one that is the most involved.
        if (mobilityProfile.mobilityDevices.contains("none")) {
            mobilityProfile.mobilityDevices.clear();
        } else {
            if (mobilityProfile.mobilityDevices.contains("white cane")) {
                visionTemp = "Blind";
            }
            if (mobilityProfile.mobilityDevices.contains("manual walker")
                    || mobilityProfile.mobilityDevices.contains("wheeled walker")
                    || mobilityProfile.mobilityDevices.contains("cane")
                    || mobilityProfile.mobilityDevices.contains("crutches")
                    || mobilityProfile.mobilityDevices.contains("stroller")
                    || mobilityProfile.mobilityDevices.contains("service animal")) {
                mModeTemp = "Device";
            }
            if (mobilityProfile.mobilityDevices.contains("mobility scooter")) {
                mModeTemp = "MScooter";
            }
            if (mobilityProfile.mobilityDevices.contains("electric wheelchair")) {
                mModeTemp = "WChairE";
            }
            if (mobilityProfile.mobilityDevices.contains("manual wheelchair")) {
                mModeTemp = "WChairM";
            }

            if ("None".equals(mModeTemp) && mobilityProfile.isMobilityLimited) {
                mModeTemp = "Some";
            }
        }

        if (MobilityProfile.VisionLimitation.LOW_VISION == mobilityProfile.visionLimitation) {
            visionTemp = "LowVision";
        } else if (MobilityProfile.VisionLimitation.LEGALLY_BLIND == mobilityProfile.visionLimitation) {
            visionTemp = "Blind";
        }

        // Create combinations for mobility mode and vision
        if (Set.of("LowVision", "Blind").contains(visionTemp)) {
            if ("None".equals(mModeTemp)) {
                mobilityProfile.mobilityMode = visionTemp;
            } else if (MOBILITY_DEVICES.contains(mModeTemp)) {
                mobilityProfile.mobilityMode = mModeTemp + "-" + visionTemp;
            }
        } else if (MOBILITY_DEVICES.contains(mModeTemp)) {
            mobilityProfile.mobilityMode = mModeTemp;
        }
        mobilityProfile.mobilityMode = "None";
    }
}
