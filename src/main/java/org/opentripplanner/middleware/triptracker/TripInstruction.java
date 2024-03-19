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
 * Instructions to be provided to the user depending on where they are on their journey.
 */
public enum TripInstruction {

//    GET_ON_BUS,
//    STAY_ON_BUS,
//    PREPARE_TO_GET_OFF_BUS,
//    GET_OFF_BUS,
    NO_INSTRUCTION;

    // More instruction types will be added.

    /**
     * Provides the instructions for the user based on the trip stage and location.
     */
//    public static String getInstructions(TripStage tripStage) {
//        // This is to be expanded on in later PRs. For now, it is used for unit testing.
//        switch (tripStage) {
//            case START:
//                return TripInstruction.GET_ON_BUS.name();
//            case UPDATE:
//                return TripInstruction.STAY_ON_BUS.name();
//            default:
//                return TripInstruction.NO_INSTRUCTION.name();
//        }
//    }

    /**
     * Define the trip instruction based on the traveler's current position compared to expected and nearest points on the trip.
     */
    public static String getTripInstruction(
        TripStatus tripStatus,
        TravelerPosition travelerPosition
    ) {
        if (hasRequiredTripStatus(tripStatus) &&
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.mode.equalsIgnoreCase("walk")
        ) {
            if (travelerPosition.segmentFromTime != null) {
                return createInstruction(travelerPosition.expectedLeg, travelerPosition.segmentFromTime);
            }
            if (travelerPosition.segmentFromPosition != null) {
                return createInstruction(travelerPosition.expectedLeg, travelerPosition.segmentFromPosition);
            }
            return TripInstruction.NO_INSTRUCTION.name();
        }
        return TripInstruction.NO_INSTRUCTION.name();
    }

    /**
     * The trip instruction can only be provided if the trip status is defined as on or close to schedule.
     */
    private static boolean hasRequiredTripStatus(TripStatus tripStatus) {
        return
            !tripStatus.equals(TripStatus.NO_STATUS) &&
            !tripStatus.equals(TripStatus.DEVIATED) &&
            !tripStatus.equals(TripStatus.ENDED);
    }

    private static String createInstruction(Leg expectedLeg, Segment activeSegment) {
        StepDistance nearest = getNearestStep(expectedLeg, activeSegment);
        List<StepSegment> stepSegments = getStepSegments(expectedLeg);
        StepSegment activeStep = getStepEncompassingSegment(stepSegments, activeSegment);
        if (activeStep != null && onSimilarBearing(activeStep, activeSegment)) {
            double distanceToNextStep = Math.round(getDistance(activeStep.end, activeSegment.end));
            String nextStepInstruction = getNextStepInstruction(expectedLeg.steps, activeStep.stepIndex);
            String directionInstruction = getRelativeDirectionInstruction(nextStepInstruction);
            if (distanceToNextStep <= 2) {
                return directionInstruction;
            } else {
                return String.format("In %sm %s.", distanceToNextStep, directionInstruction);
            }
        } else if (nearest != null) {
            double distance = Math.round(nearest.distance);
            String directionInstruction = getRelativeDirectionInstruction(nearest.step.relativeDirection);
            if (distance <= 2) {
                return directionInstruction;
            } else {
                return String.format("In %sm %s.", distance, directionInstruction);
            }
        }
        return TripInstruction.NO_INSTRUCTION.name();
    }

    @Nullable
    private static String getNextStepInstruction(List<Step> steps, int stepIndex) {
        if (stepIndex + 1 <= steps.size()) {
            return steps.get(stepIndex + 1).relativeDirection;
        } else {
            return null;
        }
    }

    private static String getRelativeDirectionInstruction(String relativeDirection) {
        switch (relativeDirection) {
            case "DEPART":
            case "CONTINUE":
            case "CIRCLE_CLOCKWISE":
            case "CIRCLE_COUNTERCLOCKWISE":
            case "UTURN_LEFT":
            case "UTURN_RIGHT":
            case "ENTER_STATION":
            case "EXIT_STATION":
            case "FOLLOW_SIGNS":
                return relativeDirection;
            case "ELEVATOR":
                return "enter " + relativeDirection;
            default:
                return "turn " + relativeDirection;
        }
    }

    private static List<StepSegment> getStepSegments(Leg leg) {
        List<StepSegment> stepSegments = new ArrayList<>();
        for (int i=0; i<leg.steps.size()-1; i++) {
            stepSegments.add(new StepSegment(
                new Coordinates(leg.steps.get(i)),
                new Coordinates(leg.steps.get(i+1)),
                i
            ));
        }
        return stepSegments;
    }

    /**
     * Confirm that the two segments are on the same bearing within 10 degrees.
     */
    private static boolean onSimilarBearing(StepSegment segmentOne, Segment segmentTwo) {
        double segmentOneBearing = GeometryUtils.calculateBearing(segmentOne.start, segmentOne.end);
        double segmentTwoBearing = GeometryUtils.calculateBearing(segmentTwo.start, segmentTwo.end);
        return Math.abs(segmentOneBearing - segmentTwoBearing) < 10;
    }

    /**
     * Get the step that contains the travelers current segment.
     */
    @Nullable
    private static StepSegment getStepEncompassingSegment(List<StepSegment> steps, Segment segment) {
        for (StepSegment stepSegment : steps) {
            if (isPointBetween(stepSegment.start, stepSegment.end, segment.end)) {
                // End of segment is within the step segment.
                return stepSegment;
            }
        }
        return null;
    }

    /**
     * Get the step that is nearest to the trip segment.
     */
    @Nullable
    private static StepDistance getNearestStep(Leg leg, Segment segment) {
        StepDistance nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Step step : leg.steps) {
            double distance = getDistanceFromLine(segment.start, segment.end, new Coordinates(step));
            if (distance < nearestDistance) {
                nearest = new StepDistance(step, distance);
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static class StepDistance {

        private final Step step;

        private final double distance;

        public StepDistance(Step step, double distance) {
            this.step = step;
            this.distance = distance;
        }
    }
}
