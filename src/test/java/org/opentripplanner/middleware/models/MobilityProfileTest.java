package org.opentripplanner.middleware.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This class contains tests of selected scenarios in {@link MobilityProfile}.
 */
public class MobilityProfileTest {
    // The mobility modes tested are tightly coupled with algorithms in the
    // Georgia Tech Mobility Profile Configuration / Logical Flow document, as
    // implemented in the MobilityPorfile#updateMobilityMode() method.  Changes
    // to that document must be reflected in that method and in these tests.

    private static Stream<Arguments> provideModes() {
        return Stream.of(
            Arguments.of(Set.of("service animal", "crutches"), "Device"),
            Arguments.of(Set.of("service animal", "electric wheelchair"), "WChairE"),
            Arguments.of(Set.of("service animal", "electric wheelchair", "white cane"), "WChairE-Blind"),
            Arguments.of(Set.of("manual wheelchair", "electric wheelchair", "white cane"), "WChairM-Blind"),
            Arguments.of(Collections.EMPTY_SET, "None"),
            Arguments.of(Set.of("cardboard transmogrifier"), "None"), // Unknown/invalid device
            Arguments.of(Set.of("cane", "none", "service animal"), "None") // Devices include "none" poison pill
        );
    }

    @ParameterizedTest
    @MethodSource("provideModes")
    public void testModes(Set<String> devices, String mode) {
        var prof = new MobilityProfile();
        prof.mobilityDevices = devices;
        prof.updateMobilityMode();
        Assertions.assertEquals(mode, prof.mobilityMode);
    }

    private static Stream<Arguments> provideModesVision() {
        return Stream.of(
            Arguments.of(MobilityProfile.VisionLimitation.LOW_VISION, // Overrides "white cane" default
                    Set.of("service animal", "electric wheelchair", "white cane"), "WChairE-LowVision"),
            Arguments.of(MobilityProfile.VisionLimitation.LEGALLY_BLIND,
                    Set.of("manual wheelchair", "stroller"), "WChairM-Blind")
        );
    }

    @ParameterizedTest
    @MethodSource("provideModesVision")
    public void testModesVision(MobilityProfile.VisionLimitation limitation, Set<String> devices, String mode) {
        var prof = new MobilityProfile();
        prof.mobilityDevices = devices;
        prof.visionLimitation = limitation;
        prof.updateMobilityMode();
        Assertions.assertEquals(mode, prof.mobilityMode);
    }
}
