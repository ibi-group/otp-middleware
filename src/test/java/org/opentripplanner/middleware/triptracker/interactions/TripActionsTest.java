package org.opentripplanner.middleware.triptracker.interactions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.triptracker.Segment;
import org.opentripplanner.middleware.utils.Coordinates;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripActionsTest {
    @ParameterizedTest
    @MethodSource("createMatchSegmentCases")
    void canMatchSegment(Segment segment, String expectedActionId, String message) {

        TripActions tripActions = new TripActions(List.of(
            new SegmentAction(
                "segment1",
                new Segment(
                    new Coordinates(33.95684, -83.97971),
                    new Coordinates(33.95653, -83.97973)
                ),
                ""
            ),
            new SegmentAction(
                "segment2",
                new Segment(
                    new Coordinates(33.95173, -83.98153),
                    new Coordinates(33.95154, -83.98121)
                ),
                ""
            )
        ));

        SegmentAction segmentAction = tripActions.getSegmentAction(segment);
        assertEquals(expectedActionId, segmentAction != null ? segmentAction.id : null, message);
    }

    static Stream<Arguments> createMatchSegmentCases() {
        return Stream.of(
            Arguments.of(
                new Segment(
                    new Coordinates(33.95444, -83.98013),
                    new Coordinates(33.95573, -83.97991)
                ),
                null,
                "Segment not near a trip action should not match configured segments"
            ),
            Arguments.of(
                new Segment(
                    new Coordinates(33.95684, -83.97971),
                    new Coordinates(33.95653, -83.97973)
                ),
                "segment1",
                "Segment with coords about the same as a trip action should match"
            )
        );
    }
}
