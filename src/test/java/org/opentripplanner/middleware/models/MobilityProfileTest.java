package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

/**
 * This class contains tests of selected scenarios in {@link MobilityProfile}.
 */
public class MobilityProfileTest {
    // The mobility modes tested are tightly coupled with algorithms in the
    // Georgia Tech Mobility Profile Configuration / Logical Flow document, as
    // implemented in the MobilityPorfile#updateMobilityMode() method.  Changes
    // to that document msut be reflected in that method and in these tests.

    @Test
    public void testModesKnown() {
        var prof = new MobilityProfile();
        prof.mobilityDevices = Set.of("service animal", "crutches");
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "Device");
        prof.mobilityDevices = Set.of("service animal", "electric wheelchair");
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "WChairE");
        prof.mobilityDevices = Set.of("service animal", "electric wheelchair", "white cane");
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "WChairE-Blind");
        prof.mobilityDevices = Set.of("manual wheelchair", "electric wheelchair", "white cane");
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "WChairM-Blind");
    }

    @Test
    public void testModesNone() {
        var prof = new MobilityProfile();
        prof.mobilityDevices = Collections.EMPTY_LIST;
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "None");
        prof.mobilityDevices = Set.of("cardboard transmogrifier"); // Unknown/invalid device
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "None");
        prof.mobilityDevices = Set.of("cane", "none", "service animal"); // Devices with "none" poison pill
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "None");
    }

    @Test
    public void testModesVision() {
        var prof = new MobilityProfile();
        prof.mobilityDevices = Set.of("service animal", "electric wheelchair", "white cane");
        prof.visionLimitation = MobilityProfile.VisionLimitation.LOW_VISION; // Overrides "white cane" effect
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "WChairE-LowVision");
        prof.mobilityDevices = Set.of("manual wheelchair", "stroller");
        prof.visionLimitation = MobilityProfile.VisionLimitation.LEGALLY_BLIND;
        prof.updateMobilityMode();
        Assertions.assertEquals(prof.mobilityMode, "WChairM-Blind");
    }
}
