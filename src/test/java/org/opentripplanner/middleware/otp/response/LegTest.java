package org.opentripplanner.middleware.otp.response;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LegTest {

    /**
     * Runs a parameterized test to check if the equals method works as expected for all Leg combinations generated in
     * the {@link LegTest#createLegComparisons()} method.
     */
    @ParameterizedTest
    @MethodSource("createLegComparisons")
    public void testEquals (LegEqualityTestCase testCase) {
        assertEquals(testCase.shouldBeEqual, testCase.a.equals(testCase.b), testCase.message);
    }

    private static List<LegEqualityTestCase> createLegComparisons() throws CloneNotSupportedException, IOException {
        List<LegEqualityTestCase> legComparisons = new ArrayList<>();

        // same exact walking leg
        Leg walkLeg = TestUtils.getResourceFileContentsAsJSON("otp/response/walkLeg.json", Leg.class);
        legComparisons.add(new LegEqualityTestCase(
            walkLeg,
            walkLeg.clone(),
            "walk leg clone should be equal",
            true
        ));

        // TODO: analyze more possibilities such as:
        //   a leg's place's GPS coordinates changed very very slightly
        //   a floating vehicle rental no longer has enough range to complete the journey
        //   floating vehicle pricing has changed
        //   TNC pricing has changed

        return legComparisons;
    }

    private static String createFeedScopedId(String feedId, String entityId) {
        return String.format("%s:%s", feedId, entityId);
    }

    /**
     * A helper class to send to the {@link LegTest#testEquals(LegEqualityTestCase)} test method
     */
    private static class LegEqualityTestCase {
        /* comparison Leg a */
        public final Leg a;
        /* comparison Leg b */
        public final Leg b;
        /* a helpful message describing the particular test case */
        public final String message;
        /* if true, Leg a and Leg b are expected to be equal. */
        public final boolean shouldBeEqual;

        public LegEqualityTestCase(Leg a, Leg b, String message, boolean shouldBeEqual) {
            this.a = a;
            this.b = b;
            this.message = message;
            this.shouldBeEqual = shouldBeEqual;
        }
    }
}
