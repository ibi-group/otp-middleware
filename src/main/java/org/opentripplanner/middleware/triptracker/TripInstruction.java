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

    private static final int FIRST_STEP_BOUNDARY = 10;

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
            return buildInstruction(alignPositionToStep(travelerPosition));
        }
        return NO_INSTRUCTION;
    }

    /**
     * The trip instruction can only be provided if the traveler is close to the indicated route.
     */
    private static boolean hasRequiredTripStatus(TripStatus tripStatus) {
        return
            !tripStatus.equals(TripStatus.NO_STATUS) &&
            !tripStatus.equals(TripStatus.DEVIATED) &&
            !tripStatus.equals(TripStatus.ENDED);
    }

    /**
     * If available, the leg segment from time takes precedence over the leg segment from position.
     */
    @Nullable
    private static LegSegment getActiveLegSegment(TravelerPosition travelerPosition) {
        return (travelerPosition.legSegmentFromTime != null)
            ? travelerPosition.legSegmentFromTime
            : travelerPosition.legSegmentFromPosition;
    }

    /**
     * Align the traveler's position to the nearest step.
     */
    @Nullable
    public static AlignedStep alignPositionToStep(TravelerPosition travelerPosition) {
        LegSegment activeLegSegment = getActiveLegSegment(travelerPosition);
        if (activeLegSegment == null) {
            return null;
        }

        StepSegment activeStep = getStepEncompassingSegment(travelerPosition.expectedLeg, activeLegSegment);
        if (activeStep != null && isOnSimilarBearing(activeStep, activeLegSegment)) {
            // High confidence.
            double distanceToNextStep = Math.round(getDistance(activeStep.end, activeLegSegment.end));
            return new AlignedStep(
                distanceToNextStep,
                getStep(travelerPosition.expectedLeg.steps, activeStep.stepIndex + 1)
            );
        }

        StepSegment nearestStep = getNearestStep(travelerPosition.expectedLeg, activeLegSegment);
        if (nearestStep == null) {
            return null;
        }

        // Lower confidence. This part could benefit from "traveler bearing processing".
        Coordinates destination = new Coordinates(travelerPosition.expectedLeg.to);
        if (nearestStep.stepIndex == 0) {
            // First step.
            double distance = getDistance(travelerPosition.currentPosition, nearestStep.start);
            return (distance < FIRST_STEP_BOUNDARY)
                ? new AlignedStep(distance, getStep(travelerPosition.expectedLeg.steps, nearestStep.stepIndex))
                : null;
        } else if (isLastStep(nearestStep.stepIndex, travelerPosition.expectedLeg)) {
            // Last step.
            if (!isLegSegmentNearerToDestination(nearestStep, activeLegSegment, destination)) {
                // Assuming traveler is approaching the last step.
                return new AlignedStep(
                    getDistance(activeLegSegment.start, nearestStep.start),
                    getStep(travelerPosition.expectedLeg.steps, nearestStep.stepIndex)
                );
            }
        } else {
            // Between the first and last step.
            if (isLegSegmentNearerToDestination(nearestStep, activeLegSegment, destination)) {
                // Assuming traveler is passed the nearest step.
                Step nextStep = travelerPosition.expectedLeg.steps.get(nearestStep.stepIndex + 1);
                return new AlignedStep(
                    getDistance(activeLegSegment.end, new Coordinates(nextStep)),
                    nextStep
                );
            } else {
                // Assuming traveler is approaching the nearest step.
                return new AlignedStep(
                    getDistance(activeLegSegment.start, nearestStep.start),
                    getStep(travelerPosition.expectedLeg.steps, nearestStep.stepIndex)
                );
            }
        }
        return null;
    }

    /**
     * Does the step index reference the last step in the leg.
     */
    private static boolean isLastStep(int stepIndex, Leg expectedLeg) {
        return stepIndex == (expectedLeg.steps.size() - 1);
    }

    /**
     * Determine if the leg segment is near to the destination than the nearest step.
     */
    private static boolean isLegSegmentNearerToDestination(
        StepSegment nearestStep,
        LegSegment activeLegSegment,
        Coordinates destination
    ) {
        double distanceFromLastStepToDestination = getDistance(nearestStep.start, destination);
        double distanceFromActiveSegmentToDestination = getDistance(activeLegSegment.start, destination);
        return distanceFromActiveSegmentToDestination < distanceFromLastStepToDestination;
    }

    /**
     * Build instruction based on distance and step instructions. e.g.
     * <p>
     * "UPCOMING: CONTINUE on Langley Drive"
     * "IMMEDIATE: RIGHT on service road"
     * "IMMEDIATE: Head WEST on sidewalk"
     * <p>
     * TODO: Internationalization and refinements to these generated instructions with input from the mobile app team.
     */
    public static String buildInstruction(AlignedStep alignedStep) {
        if (alignedStep != null && alignedStep.prefix != null) {
            String relativeDirection = (alignedStep.legStep.relativeDirection.equals("DEPART"))
                ? "Head " + alignedStep.legStep.absoluteDirection
                : alignedStep.legStep.relativeDirection;
            return String.format("%s%s on %s", alignedStep.prefix, relativeDirection, alignedStep.legStep.streetName);
        }
        return NO_INSTRUCTION;
    }

    /**
     * Get the step matching the provided index.
     */
    @Nullable
    private static Step getStep(List<Step> steps, int stepIndex) {
        return (stepIndex <= steps.size())
            ? steps.get(stepIndex)
            : null;
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
    private static StepSegment getStepEncompassingSegment(Leg expectedLeg, LegSegment legSegment) {
        for (StepSegment stepSegment : getStepSegments(expectedLeg)) {
            if (
                isPointBetween(stepSegment.start, stepSegment.end, legSegment.start) &&
                isPointBetween(stepSegment.start, stepSegment.end, legSegment.end)
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
    private static StepSegment getNearestStep(Leg leg, LegSegment legSegment) {
        StepSegment nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < leg.steps.size(); i++) {
            double distance = getDistanceFromLine(legSegment.start, legSegment.end, new Coordinates(leg.steps.get(i)));
            if (distance < nearestDistance) {
                nearest = new StepSegment(leg.steps.get(i), i);
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
