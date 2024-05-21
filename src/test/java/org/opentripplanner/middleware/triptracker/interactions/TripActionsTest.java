package org.opentripplanner.middleware.triptracker.interactions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.triptracker.Segment;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TripActionsTest {
    public static final Coordinates SEGMENT1_START = new Coordinates(33.95684, -83.97971);
    public static final Coordinates SEGMENT1_END = new Coordinates(33.95653, -83.97973);
    public static final Coordinates SOME_SEGMENT_END = new Coordinates(33.95573, -83.97991);
    public static final Coordinates SOME_SEGMENT_START = new Coordinates(33.95444, -83.98013);

    private final TripActions tripActions = new TripActions(List.of(
        new SegmentAction(
            "segment1",
            new Segment(SEGMENT1_START, SEGMENT1_END),
            TrivialTripAction.class.getName()
        ),
        new SegmentAction(
            "segment2",
            new Segment(
                new Coordinates(33.95173, -83.98153),
                new Coordinates(33.95154, -83.98121)
            ),
            TrivialTripAction.class.getName()
        )
    ));

    @BeforeEach
    void setUp() {
        TrivialTripAction.setLastSegmentId(null);
    }

    @ParameterizedTest
    @MethodSource("createMatchSegmentCases")
    void canMatchSegmentAndTriggerAction(Segment segment, String expectedActionId, String message) {
        SegmentAction segmentAction = tripActions.getSegmentAction(segment);
        assertEquals(expectedActionId, segmentAction != null ? segmentAction.id : null, message);

        assertNull(TrivialTripAction.getLastSegmentId());
        tripActions.handleSegmentAction(segment, null);
        assertEquals(expectedActionId, TrivialTripAction.getLastSegmentId(), message);
    }

    static Stream<Arguments> createMatchSegmentCases() {
        return Stream.of(
            Arguments.of(
                new Segment(SOME_SEGMENT_START, SOME_SEGMENT_END),
                null,
                "Segment not near a trip action should not match configured segments"
            ),
            Arguments.of(
                new Segment(SEGMENT1_START, SEGMENT1_END),
                "segment1",
                "Segment with coordinates about the same as a trip action should match"
            )
        );
    }

    private Step createStep(Coordinates coords) {
        Step step = new Step();
        step.lat = coords.lat;
        step.lon = coords.lon;
        return step;
    }

    @ParameterizedTest
    @MethodSource("createMatchSegmentFromLegStepsCases")
    void canMatchSegmentFromLegSteps(int stepIndex, String expectedActionId, String message) {
        Step stepBeforeSegment1 = createStep(SOME_SEGMENT_END);
        Step stepOnSegment1 = createStep(SEGMENT1_START);
        Step stepAfterSegment1 = createStep(SEGMENT1_END);

        List<Step> steps = List.of(
            stepBeforeSegment1,
            stepOnSegment1,
            stepAfterSegment1
        );

        assertNull(TrivialTripAction.getLastSegmentId());
        tripActions.handleSegmentAction(steps.get(stepIndex), steps, null);
        assertEquals(expectedActionId, TrivialTripAction.getLastSegmentId(), message);
    }

    static Stream<Arguments> createMatchSegmentFromLegStepsCases() {
        return Stream.of(
            Arguments.of(
                0,
                null,
                "Leg step not near a trip action should not match configured segments"
            ),
            Arguments.of(
                1,
                "segment1",
                "Leg step with coordinates about the same as a trip action should match"
            ),
            Arguments.of(
                2,
                null,
                "Leg step not near a trip action should not match configured segments"
            )
        );
    }
}
