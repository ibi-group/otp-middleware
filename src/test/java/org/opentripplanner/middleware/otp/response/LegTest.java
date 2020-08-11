package org.opentripplanner.middleware.otp.response;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LegTest {

    /**
     * Runs a parameterized test to check if the equals method works as expected for all Leg combinations generated in
     * the {@link LegTest#createLegComparisons()} method.
     */
    @ParameterizedTest
    @MethodSource("createLegComparisons")
    public void testEquals (TestEqualsTestCase testCase) {
        assertEquals(testCase.shouldBeEqual, testCase.a.equals(testCase.b), testCase.message);
    }

    private static List<TestEqualsTestCase> createLegComparisons() throws CloneNotSupportedException, IOException {
        List<TestEqualsTestCase> legComparisons = new ArrayList<>();

        // same exact walking leg
        Leg walkLeg = getResourceFileContentsAsJSON("walkLeg.json");
        legComparisons.add(new TestEqualsTestCase(
            walkLeg,
            walkLeg.clone(),
            "walk leg clone should be equal",
            true
        ));

        // a walking leg that takes the same time, but takes a different route
        Leg walkLegAlongAnotherPathWithSameTime = walkLeg.clone();
        walkLegAlongAnotherPathWithSameTime.distance = 180.0;
        walkLegAlongAnotherPathWithSameTime.legGeometry.points = "blahblah";
        Step anotherPath = walkLeg.steps.get(0).clone();
        anotherPath.streetName = "another path";
        walkLegAlongAnotherPathWithSameTime.steps = new ArrayList<>(Collections.singleton(anotherPath));
        legComparisons.add(new TestEqualsTestCase(
            walkLeg,
            walkLegAlongAnotherPathWithSameTime,
            "walk across park on another path that takes the same time should not be equal",
            false
        ));

        // the underlying transit feed changed an internal id, but everything else about the transit trip is the same
        Leg transitLeg = getResourceFileContentsAsJSON("transitLeg.json");
        String newFeedId = "we-changed-our-feed-id-and-nothing-else-hahahaha";
        Leg transitLegWithDifferentFeedId = transitLeg.clone();
        transitLegWithDifferentFeedId.routeId = createFeedScopedId(newFeedId, "203");
        transitLegWithDifferentFeedId.tripId = createFeedScopedId(newFeedId, "10122171");
        transitLegWithDifferentFeedId.from.stopId = createFeedScopedId(newFeedId, "13070");
        transitLegWithDifferentFeedId.to.stopId = createFeedScopedId(newFeedId, "13069");
        legComparisons.add(new TestEqualsTestCase(
            transitLeg,
            transitLegWithDifferentFeedId,
            "transit leg with different underlying ids, but where everything else is the same should be equal",
            true
        ));

        // TODO: analyze more possibilities such as:
        //   a floating vehicle rental no longer has enough range to complete the journey
        //   floating vehicle pricing has changed
        //   TNC pricing has changed

        return legComparisons;
    }

    private static String createFeedScopedId(String feedId, String entityId) {
        return String.format("%s:%s", feedId, entityId);
    }

    private static Leg getResourceFileContentsAsJSON(String jsonFilename) throws IOException {
        return TestUtils.getResourceFileContentsAsJSON("otp/response/" + jsonFilename, Leg.class);
    }

    /**
     * A helper class to send to the {@link LegTest#testEquals(TestEqualsTestCase)} test method
     */
    private static class TestEqualsTestCase {
        /* comparison Leg a */
        public final Leg a;
        /* comparison Leg b */
        public final Leg b;
        /* a helpful message describing the particular test case */
        public final String message;
        /* if true, Leg a and Leg b are expected to be equal. */
        public final boolean shouldBeEqual;

        public TestEqualsTestCase(Leg a, Leg b, String message, boolean shouldBeEqual) {
            this.a = a;
            this.b = b;
            this.message = message;
            this.shouldBeEqual = shouldBeEqual;
        }
    }
}
