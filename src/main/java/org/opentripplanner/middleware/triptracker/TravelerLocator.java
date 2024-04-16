package org.opentripplanner.middleware.triptracker;

import io.leonard.PolylineUtils;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.utils.Coordinates;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.triptracker.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;
import static org.opentripplanner.middleware.utils.GeometryUtils.isPointBetween;

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
        if (hasRequiredTripStatus(tripStatus) && hasRequiredWalkLeg(travelerPosition)
        ) {
            TripInstruction tripInstruction = alignTravelerToTrip(travelerPosition, isStartOfTrip);
            if (tripInstruction != null) {
                return tripInstruction.build();
            }
        }

        if (tripStatus.equals(TripStatus.DEVIATED) && hasRequiredWalkLeg(travelerPosition)
        ) {
            TripInstruction tripInstruction = getBackOnTrack(travelerPosition);
            if (tripInstruction != null) {
                return tripInstruction.build();
            }
        }
        return NO_INSTRUCTION;
    }

    /**
     * Has required walk leg.
     */
    private static boolean hasRequiredWalkLeg(TravelerPosition travelerPosition) {
        return
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.mode.equalsIgnoreCase("walk");
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
     * Suggest the closest street to head towards if deviated from trip.
     */
    @Nullable
    private static TripInstruction getBackOnTrack(TravelerPosition travelerPosition) {
        Step nearestStep = snapToStep(travelerPosition);
        return (nearestStep != null)
            ? new TripInstruction(nearestStep.streetName)
            : null;
    }

    /**
     * Align the traveler's position to the nearest step or destination.
     */
    @Nullable
    public static TripInstruction alignTravelerToTrip(TravelerPosition travelerPosition, boolean isStartOfTrip) {

        if (isStartOfTrip) {
            // If the traveler has just started the trip and is within a set distance of the first step.
            Step firstStep = travelerPosition.expectedLeg.steps.get(0);
            if (firstStep == null) {
                return null;
            }
            double distance = getDistance(travelerPosition.currentPosition, new Coordinates(firstStep));
            return (distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS)
                ? new TripInstruction(distance, firstStep)
                : null;
        }

        Coordinates legDestination = new Coordinates(travelerPosition.expectedLeg.to);
        double distanceToDestination = getDistance(travelerPosition.currentPosition, legDestination);
        if (distanceToDestination <= TRIP_INSTRUCTION_UPCOMING_RADIUS) {
            // Assuming traveler is approaching the destination.
            return new TripInstruction(distanceToDestination, travelerPosition.expectedLeg.to.name);
        }

        Step nextStep = snapToStep(travelerPosition);
        if (nextStep != null) {
            return new TripInstruction(
                getDistance(travelerPosition.currentPosition, new Coordinates(nextStep)),
                nextStep
            );
        }
        return null;
    }

    /**
     * Align the traveler to the leg and provide the next step from this point forward.
     */
    private static Step snapToStep(TravelerPosition travelerPosition) {
        List<Coordinates> legPositions = injectStepsIntoLegPositions(travelerPosition.expectedLeg);
        int pointIndex = getNearestPointIndex(legPositions, travelerPosition.currentPosition);
        return (pointIndex != -1)
            ? getNextStep(travelerPosition.expectedLeg, legPositions, pointIndex)
            : null;
    }

    /**
     * From the starting index, find the next step along the leg.
     */
    public static Step getNextStep(Leg leg, List<Coordinates> positions, int startIndex) {
        for (int i = startIndex; i < positions.size(); i++) {
            for (Step step : leg.steps) {
                if (positions.get(i).equals(new Coordinates(step))) {
                    return step;
                }
            }
        }
        return null;
    }

    /**
     * Get the point index on the leg which is nearest to position.
     */
    private static int getNearestPointIndex(List<Coordinates> positions, Coordinates position) {
        int pointIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < positions.size(); i++) {
            double distance = getDistance(position, positions.get(i));
            if (distance < nearestDistance) {
                pointIndex = i;
                nearestDistance = distance;
            }
        }
        return pointIndex;
    }

    /**
     * Inject the step positions into the leg positions. It is assumed that both sets of points are on the same route
     * and are in between the start and end positions. If b = beginning, p = point on leg, S = step and e = end, create
     * a list of coordinates which can be traversed to get the next step.
     * <p>
     * b|p|S|p|p|p|p|p|p|S|p|p|S|p|p|p|p|p|S|e
     */
    public static List<Coordinates> injectStepsIntoLegPositions(Leg leg) {
        List<Coordinates> allPositions = getAllLegPositions(leg);
        List<Step> injectedSteps = new ArrayList<>();
        List<Coordinates> finalPositions = new ArrayList<>();
        for (int i = 0; i < allPositions.size() - 1; i++) {
            Coordinates p1 = allPositions.get(i);
            finalPositions.add(p1);
            Coordinates p2 = allPositions.get(i + 1);
            for (Step step : leg.steps) {
                if (isPointBetween(p1, p2, new Coordinates(step)) && !injectedSteps.contains(step)) {
                    finalPositions.add(new Coordinates(step));
                    injectedSteps.add(step);
                }
            }
        }

        // Add the destination coords which are missed because of the -1 condition above.
        finalPositions.add(allPositions.get(allPositions.size() - 1));

        if (injectedSteps.size() != leg.steps.size()) {
            // One or more steps have not been injected because they are not between two geometry points. Inject these
            // based on proximity.
            List<Step> missedSteps = leg.steps
                .stream()
                .filter(step -> !injectedSteps.contains(step))
                .collect(Collectors.toList());
            for (Step missedStep : missedSteps) {
                int pointIndex = getNearestPointIndex(finalPositions, new Coordinates(missedStep));
                if (pointIndex != -1) {
                    finalPositions.add(pointIndex, new Coordinates(missedStep));
                }
            }
        }
        return finalPositions;
    }

    /**
     * Get a list containing all positions on a leg.
     */
    public static List<Coordinates> getAllLegPositions(Leg leg) {
        List<Coordinates> allPositions = new ArrayList<>();
        allPositions.add(new Coordinates(leg.from));
        allPositions.addAll(getLegGeoPoints(leg));
        allPositions.add(new Coordinates(leg.to));
        return allPositions;
    }

    /**
     * Get leg geometry points as coordinates and remove duplicates.
     */
    public static List<Coordinates> getLegGeoPoints(Leg leg) {
        return PolylineUtils
            .decode(leg.legGeometry.points, 5)
            .stream()
            .distinct()
            .map(Coordinates::new)
            .collect(Collectors.toList());
    }
}
