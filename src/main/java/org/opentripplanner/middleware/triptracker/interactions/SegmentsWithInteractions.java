package org.opentripplanner.middleware.triptracker.interactions;

import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.triptracker.Segment;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.GeometryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Holds segments with configured interactions. */
public class SegmentsWithInteractions {
    private static final Logger LOG = LoggerFactory.getLogger(SegmentsWithInteractions.class);

    public static final String DEFAULT_SEGMENTS = "configurations/default/segments.json";

    public static final SegmentsWithInteractions KNOWN_INTERACTIONS;

    public List<SegmentAction> segments = new ArrayList<>();

    static {
        try (InputStream stream = new FileInputStream(DEFAULT_SEGMENTS)) {
            KNOWN_INTERACTIONS = JsonUtils.getPOJOFromJSON(stream, SegmentsWithInteractions.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SegmentsWithInteractions() {
        // For persistence
    }

    /**
     * @param segment The {@link Segment} to test
     * @return The first {@link SegmentAction} found for the given segment
     */
    public static SegmentAction getSegmentAction(Segment segment) {
        for (SegmentAction a : KNOWN_INTERACTIONS.segments) {
            if (segmentMatchesAction(segment, a)) {
                return a;
            }
        }
        return null;
    }

    public static boolean segmentMatchesAction(Segment segment, SegmentAction action) {
        final int MAX_RADIUS = 10; // meters // TODO: get this from config.
        return (GeometryUtils.getDistance(segment.start, action.start) <= MAX_RADIUS && GeometryUtils.getDistance(segment.end, action.end) <= MAX_RADIUS)
            ||
            (GeometryUtils.getDistance(segment.start, action.end) <= MAX_RADIUS && GeometryUtils.getDistance(segment.end, action.start) <= MAX_RADIUS);
    }

    public static void handleSegmentAction(Segment segment, OtpUser otpUser) {
        SegmentAction action = getSegmentAction(segment);
        if (action != null) {
            try {
                Class<?> interactionClass = Class.forName(action.trigger);
                Interaction interaction = (Interaction) interactionClass.getDeclaredConstructor().newInstance();
                interaction.triggerAction(action, otpUser);
            } catch (Exception e) {
                LOG.error("Error instantiating class {}", action.trigger, e);
                throw new RuntimeException(e);
            }
        }
    }

    public static void handleSegmentAction(Step step, List<Step> steps, OtpUser user) {
        int stepIndex = steps.indexOf(step);
        if (stepIndex < steps.size() - 1) {
            Step stepAfter = steps.get(stepIndex + 1);
            Segment segment = new Segment(
                new Coordinates(step),
                new Coordinates(stepAfter)
            );
            handleSegmentAction(segment, user);
        }

    }
}
