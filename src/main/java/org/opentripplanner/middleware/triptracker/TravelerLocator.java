package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.utils.Coordinates;

import javax.annotation.Nullable;
import java.util.List;

import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.TripInstruction.TRIP_INSTRUCTION_UPCOMING_DISTANCE;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;

/**
 * Locate the traveler in relation to the nearest step or destination and provide the appropriate instructions.
 */
public class TravelerLocator {

    private TravelerLocator() {
    }

    /**
     * Define the instruction based on the traveler's current position compared to expected and nearest points on the
     * trip.
     */
    public static String getInstruction(
        TripStatus tripStatus,
        TravelerPosition travelerPosition,
        boolean isStartOfTrip
    ) {
        if (hasRequiredTripStatus(tripStatus) &&
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.mode.equalsIgnoreCase("walk")
        ) {
            TripInstruction tripInstruction = alignTravelerToTrip(travelerPosition, isStartOfTrip);
            if (tripInstruction != null) {
                return tripInstruction.build();
            }
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
     * Align the traveler's position to the nearest step or destination.
     */
    @Nullable
    public static TripInstruction alignTravelerToTrip(TravelerPosition travelerPosition, boolean isStartOfTrip) {

        if (isStartOfTrip) {
            // If the traveler has just started the trip and is within a set distance of the first step.
            Step firstStep = getStep(travelerPosition.expectedLeg.steps, 0);
            if (firstStep == null) {
                return null;
            }
            double distance = getDistance(travelerPosition.currentPosition, new Coordinates(firstStep));
            return (distance <= TRIP_INSTRUCTION_UPCOMING_DISTANCE)
                ? new TripInstruction(distance, firstStep)
                : null;
        }

        Coordinates destination = new Coordinates(travelerPosition.expectedLeg.to);
        double distanceToDestination = getDistance(travelerPosition.currentPosition, destination);
        if (distanceToDestination <= TRIP_INSTRUCTION_UPCOMING_DISTANCE) {
            // Assuming traveler is approaching the destination.
            return new TripInstruction(distanceToDestination, travelerPosition.expectedLeg.to.name);
        }

        int nearestStepIndex = getNearestStep(travelerPosition.expectedLeg, travelerPosition.currentPosition);
        Step nearestStep = getStep(travelerPosition.expectedLeg.steps, nearestStepIndex);
        if (nearestStep == null) {
            return null;
        }

        Coordinates nearestStepCoords = new Coordinates(nearestStep);

        if (isLastStep(nearestStepIndex, travelerPosition.expectedLeg)) {
            // Last step.
            if (isStepNearerToDestination(nearestStepCoords, travelerPosition.currentPosition, destination)) {
                // Assuming traveler is approaching the last step.
                return new TripInstruction(
                    getDistance(travelerPosition.currentPosition, nearestStepCoords),
                    getStep(travelerPosition.expectedLeg.steps, nearestStepIndex)
                );
            }
        } else {
            Step nextStep = travelerPosition.expectedLeg.steps.get(nearestStepIndex + 1);
            Coordinates nextStepCoordinates = new Coordinates(nextStep);
            if (!isStepNearerToDestination(nearestStepCoords, travelerPosition.currentPosition, nextStepCoordinates)) {
                // Assuming traveler is passed the nearest step.
                return new TripInstruction(
                    getDistance(travelerPosition.currentPosition, nextStepCoordinates),
                    nextStep
                );
            } else {
                // Assuming traveler is approaching (or on) the nearest step.
                return new TripInstruction(
                    getDistance(travelerPosition.currentPosition, nearestStepCoords),
                    getStep(travelerPosition.expectedLeg.steps, nearestStepIndex)
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
     * Determine if the step is near to the destination than the traveler.
     */
    private static boolean isStepNearerToDestination(
        Coordinates nearestStepCoords,
        Coordinates position,
        Coordinates destination
    ) {
        double distanceFromNearestStepToDestination = getDistance(nearestStepCoords, destination);
        double distanceFromPositionToDestination = getDistance(position, destination);
        return distanceFromNearestStepToDestination <= distanceFromPositionToDestination;
    }

    /**
     * Get the step matching the provided index.
     */
    @Nullable
    private static Step getStep(List<Step> steps, int stepIndex) {
        return (stepIndex != -1 && stepIndex <= steps.size())
            ? steps.get(stepIndex)
            : null;
    }

    /**
     * Get the step index that is nearest to position.
     */
    private static int getNearestStep(Leg leg, Coordinates position) {
        int nearestStepIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < leg.steps.size(); i++) {
            double distance = getDistance(position, new Coordinates(leg.steps.get(i)));
            if (distance < nearestDistance) {
                nearestStepIndex = i;
                nearestDistance = distance;
            }
        }
        return nearestStepIndex;
    }
}
