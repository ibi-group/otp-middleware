package org.opentripplanner.middleware.triptracker.interactions;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.triptracker.Segment;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.GeometryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Holds configured trip actions. */
public class TripActions {
    private static final Logger LOG = LoggerFactory.getLogger(TripActions.class);

    public static final String TRIP_ACTIONS_YML = "configurations/default/trip-actions.yml";

    private static TripActions defaultInstance;

    private final List<SegmentAction> segmentActions;

    public static TripActions getDefault() {
        if (defaultInstance == null) {
            try (InputStream stream = new FileInputStream(TRIP_ACTIONS_YML)) {
                JsonNode tripActionsYml = YamlUtils.yamlMapper.readTree(stream);
                defaultInstance = new TripActions(JsonUtils.getPOJOFromJSONAsList(tripActionsYml, SegmentAction.class));
            } catch (IOException e) {
                LOG.error("Error parsing trip-actions.yml", e);
                throw new RuntimeException(e);
            }
        }
        return defaultInstance;
    }

    public TripActions(List<SegmentAction> segmentActions) {
        this.segmentActions = segmentActions;
    }

    /**
     * @param segment The {@link Segment} to test
     * @return The first {@link SegmentAction} found for the given segment
     */
    public SegmentAction getSegmentAction(Segment segment) {
        for (SegmentAction a : segmentActions) {
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

    public void handleSegmentAction(Segment segment, OtpUser otpUser) {
        SegmentAction action = getSegmentAction(segment);
        if (action != null) {
            Interaction interaction = null;
            try {
                Class<?> interactionClass = Class.forName(action.trigger);
                interaction = (Interaction) interactionClass.getDeclaredConstructor().newInstance();
                interaction.triggerAction(action, otpUser);
            } catch (Exception e) {
                if (interaction == null) {
                    LOG.error("Error instantiating class {}", action.trigger, e);
                } else {
                    LOG.error("Could not trigger class {} on action {}", action.trigger, action.id, e);
                }
                throw new RuntimeException(e);
            }
        }
    }

    public void handleSegmentAction(Step step, List<Step> steps, OtpUser user) {
        int stepIndex = steps.indexOf(step);
        if (stepIndex >= 0 && stepIndex < steps.size() - 1) {
            Step stepAfter = steps.get(stepIndex + 1);
            Segment segment = new Segment(
                new Coordinates(step),
                new Coordinates(stepAfter)
            );
            handleSegmentAction(segment, user);
        }
    }
}
