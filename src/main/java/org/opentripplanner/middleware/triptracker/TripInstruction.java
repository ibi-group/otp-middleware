package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.GeometryUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistanceFromLine;
import static org.opentripplanner.middleware.utils.GeometryUtils.isPointBetween;

/**
 * Instructions to be provided to the traveler depending on where they are on their trip.
 */
public class TripInstruction {

    private TripInstruction() {
    }

    private static final int ACCEPTABLE_BEARING_TOLERANCE = 10;
    private static final int IMMEDIATE_INSTRUCTION_RADIUS_DISTANCE = 2;
    private static final int UPCOMING_INSTRUCTION_RADIUS_DISTANCE = 10;

    public static final String IMMEDIATE_PREFIX = "IMMEDIATE: ";
    public static final String UPCOMING_PREFIX = "UPCOMING: ";
    public static final String NO_INSTRUCTION = "NO_INSTRUCTION";

    /**
     * Define the trip instruction based on the traveler's current position compared to expected and nearest points
     * on the trip.
     */
    public static String getInstruction(
        TripStatus tripStatus,
        TravelerPosition travelerPosition
    ) {
        if (hasRequiredTripStatus(tripStatus) &&
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.mode.equalsIgnoreCase("walk")
        ) {
            if (travelerPosition.tripSegmentFromTime != null) {
                return createInstruction(travelerPosition.expectedLeg, travelerPosition.tripSegmentFromTime);
            }
            if (travelerPosition.tripSegmentFromPosition != null) {
                return createInstruction(travelerPosition.expectedLeg, travelerPosition.tripSegmentFromPosition);
            }
            return NO_INSTRUCTION;
        }
        return NO_INSTRUCTION;
    }

    /**
     * The trip instruction can only be provided if the trip is on or close to schedule.
     */
    private static boolean hasRequiredTripStatus(TripStatus tripStatus) {
        return
            !tripStatus.equals(TripStatus.NO_STATUS) &&
            !tripStatus.equals(TripStatus.DEVIATED) &&
            !tripStatus.equals(TripStatus.ENDED);
    }

    public static String createInstruction(Leg expectedLeg, TripSegment activeTripSegment) {
        StepSegment activeStep = getStepEncompassingSegment(expectedLeg, activeTripSegment);
        if (activeStep != null && isOnSimilarBearing(activeStep, activeTripSegment)) {
            // High confidence.
            double distanceToNextStep = Math.round(getDistance(activeStep.end, activeTripSegment.end));
            return getInstruction(
                distanceToNextStep,
                getStepInstruction(expectedLeg.steps, activeStep.stepIndex + 1)
            );
        }

        StepSegment nearestStep = getNearestStep(expectedLeg, activeTripSegment);

        if (nearestStep != null) {
            // Lower confidence. This part could benefit from "traveler bearing processing".
            Coordinates destination = new Coordinates(expectedLeg.to);
            if (isLastStep(nearestStep.stepIndex, expectedLeg)) {
                if (!isTripSegmentNearerToDestination(nearestStep, activeTripSegment, destination)) {
                    // Assuming traveler is approaching the last step.
                    return approachingStep(activeTripSegment, nearestStep, expectedLeg);
                }
            } else {
                if (isTripSegmentNearerToDestination(nearestStep, activeTripSegment, destination)) {
                    // Assuming traveler is passed the nearest step.
                    Step nextStep = expectedLeg.steps.get(nearestStep.stepIndex + 1);
                    return getInstruction(
                        getDistance(activeTripSegment.end, new Coordinates(nextStep)),
                        nextStep.relativeDirection
                    );
                } else {
                    // Assuming traveler is approaching the nearest step.
                    return approachingStep(activeTripSegment, nearestStep, expectedLeg);
                }
            }
        }
        return TripInstruction.NO_INSTRUCTION;
    }

    /**
     * Does the step index reference the last step in the leg.
     */
    private static boolean isLastStep(int stepIndex, Leg expectedLeg) {
        return stepIndex == (expectedLeg.steps.size() - 1);
    }

    /**
     * Provide instruction for approaching a step.
     */
    private static String approachingStep(TripSegment activeTripSegment, StepSegment nearestStep, Leg expectedLeg) {
        return getInstruction(
            getDistance(activeTripSegment.end, nearestStep.start),
            getStepInstruction(expectedLeg.steps, nearestStep.stepIndex)
        );
    }

    /**
     * Determine if the trip segment is near to the destination than the step.
     */
    private static boolean isTripSegmentNearerToDestination(
        StepSegment stepSegment,
        TripSegment tripSegment,
        Coordinates destination
    ) {
        double distanceFromLastStepToDestination = getDistance(stepSegment.start, destination);
        double distanceFromActiveSegmentToDestination = getDistance(tripSegment.start, destination);
        return distanceFromActiveSegmentToDestination < distanceFromLastStepToDestination;
    }

    /**
     * Get instruction based distance and related step instruction.
     */
    private static String getInstruction(double distance, String stepInstruction) {
        if (stepInstruction != null) {
            if (distance <= IMMEDIATE_INSTRUCTION_RADIUS_DISTANCE) {
                return IMMEDIATE_PREFIX + stepInstruction;
            } else if (distance <= UPCOMING_INSTRUCTION_RADIUS_DISTANCE) {
                return UPCOMING_PREFIX + stepInstruction;
            }
        }
        return NO_INSTRUCTION;
    }

    /**
     * Get the relative direction from a step defined by the provided index.
     */
    @Nullable
    private static String getStepInstruction(List<Step> steps, int stepIndex) {
        if (stepIndex <= steps.size()) {
            return steps.get(stepIndex).relativeDirection;
        } else {
            return null;
        }
    }

    /**
     * Create step segments based on lat/lon values. This excludes the final step because it does not have a closing
     * lat/lon.
     */
    public static List<StepSegment> getStepSegments(Leg leg) {
        List<StepSegment> stepSegments = new ArrayList<>();
        for (int i = 0; i < leg.steps.size() - 1; i++) {
            stepSegments.add(new StepSegment(
                new Coordinates(leg.steps.get(i)),
                new Coordinates(leg.steps.get(i + 1)),
                i
            ));
        }
        return stepSegments;
    }

    /**
     * Confirm that two segments are on a similar bearing within an acceptable tolerance.
     */
    private static boolean isOnSimilarBearing(Segment segmentOne, Segment segmentTwo) {
        double segmentOneBearing = GeometryUtils.calculateBearing(segmentOne.start, segmentOne.end);
        double segmentTwoBearing = GeometryUtils.calculateBearing(segmentTwo.start, segmentTwo.end);
        return Math.abs(segmentOneBearing - segmentTwoBearing) < ACCEPTABLE_BEARING_TOLERANCE;
    }

    /**
     * Get the step that contains the travelers current segment.
     */
    @Nullable
    private static StepSegment getStepEncompassingSegment(Leg expectedLeg, TripSegment tripSegment) {
        for (StepSegment stepSegment : getStepSegments(expectedLeg)) {
            if (
                isPointBetween(stepSegment.start, stepSegment.end, tripSegment.start) &&
                isPointBetween(stepSegment.start, stepSegment.end, tripSegment.end)
            ) {
                // Trip segment is within the step segment.
                return stepSegment;
            }
        }
        return null;
    }

    /**
     * Get the step that is nearest to the trip segment.
     */
    @Nullable
    private static StepSegment getNearestStep(Leg leg, TripSegment tripSegment) {
        StepSegment nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < leg.steps.size(); i++) {
            double distance = getDistanceFromLine(tripSegment.start, tripSegment.end, new Coordinates(leg.steps.get(i)));
            if (distance < nearestDistance) {
                nearest = new StepSegment(leg.steps.get(i), i);
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
