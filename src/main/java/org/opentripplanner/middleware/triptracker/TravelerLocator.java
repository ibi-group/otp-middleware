package org.opentripplanner.middleware.triptracker;

import io.leonard.PolylineUtils;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.triptracker.instruction.DeviatedInstruction;
import org.opentripplanner.middleware.triptracker.instruction.GetOffHereTransitInstruction;
import org.opentripplanner.middleware.triptracker.instruction.GetOffNextStopTransitInstruction;
import org.opentripplanner.middleware.triptracker.instruction.GetOffSoonTransitInstruction;
import org.opentripplanner.middleware.triptracker.instruction.OnTrackInstruction;
import org.opentripplanner.middleware.triptracker.instruction.TransitLegSummaryInstruction;
import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.triptracker.instruction.WaitForTransitInstruction;
import org.opentripplanner.middleware.triptracker.interactions.busnotifiers.BusOperatorActions;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.ConvertsToCoordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.NO_INSTRUCTION;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_IMMEDIATE_RADIUS;
import static org.opentripplanner.middleware.triptracker.instruction.TripInstruction.TRIP_INSTRUCTION_UPCOMING_RADIUS;
import static org.opentripplanner.middleware.utils.GeometryUtils.getDistance;
import static org.opentripplanner.middleware.utils.GeometryUtils.isPointBetween;
import static org.opentripplanner.middleware.utils.ItineraryUtils.isBusLeg;

/**
 * Locate the traveler in relation to the nearest step or destination and provide the appropriate instructions.
 */
public class TravelerLocator {

    public static final int ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES = 15;

    private static final int MIN_TRANSIT_VEHICLE_SPEED = 5; // meters per second. 11.1 mph or 18 km/h.

    private TravelerLocator() {
    }

    /**
     * Define the instruction based on the traveler's current position compared to expected and nearest points on the
     * trip.
     */
    public static TripInstruction getInstruction(
        TripStatus tripStatus,
        TravelerPosition travelerPosition,
        boolean isStartOfTrip
    ) {
        if (hasRequiredWalkLeg(travelerPosition)) {
            if (hasRequiredTripStatus(tripStatus)) { // Not DEVIATED and not ENDED.
                return alignTravelerToTrip(travelerPosition, isStartOfTrip, tripStatus);
            } else if (tripStatus.equals(TripStatus.DEVIATED)) {
                return getBackOnTrack(travelerPosition, isStartOfTrip, tripStatus);
            }
        } else if (hasRequiredTransitLeg(travelerPosition) && hasRequiredTripStatus(tripStatus)) {
            return alignTravelerToTransitTrip(travelerPosition);
        }
        return null;
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
     * Has required transit leg.
     */
    private static boolean hasRequiredTransitLeg(TravelerPosition travelerPosition) {
        return
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.transitLeg;
    }

    /**
     * The trip instruction can only be provided if the traveler is close to the indicated route.
     */
    private static boolean hasRequiredTripStatus(TripStatus tripStatus) {
        return !tripStatus.equals(TripStatus.DEVIATED) && !tripStatus.equals(TripStatus.ENDED);
    }

    /**
     * Attempt to align the deviated traveler to the trip when on access legs (e.g. walk legs).
     * If the traveler happens to be within an upcoming instruction, the instruction will be issued,
     * else suggest the closest street to head towards.
     */
    @Nullable
    private static TripInstruction getBackOnTrack(
        TravelerPosition travelerPosition,
        boolean isStartOfTrip,
        TripStatus tripStatus
    ) {
        TripInstruction instruction = alignTravelerToTrip(travelerPosition, isStartOfTrip, tripStatus);
        if (instruction != null && instruction.hasInstruction()) {
            return instruction;
        }
        Step nearestStep = snapToWaypoint(travelerPosition, travelerPosition.expectedLeg.steps);
        return (nearestStep != null)
            ? new DeviatedInstruction(nearestStep.streetName, travelerPosition.locale)
            : null;
    }

    /**
     * Align the traveler's position to the nearest step or destination.
     */
    @Nullable
    public static TripInstruction alignTravelerToTrip(
        TravelerPosition travelerPosition,
        boolean isStartOfTrip,
        TripStatus tripStatus
    ) {
        Locale locale = travelerPosition.locale;

        if (isApproachingEndOfLeg(travelerPosition)) {
            if (isBusLeg(travelerPosition.nextLeg) && isWithinOperationalNotifyWindow(travelerPosition)) {
                BusOperatorActions
                    .getDefault()
                    .handleSendNotificationAction(tripStatus, travelerPosition);
                // Regardless of whether the notification is sent or qualifies, provide a 'wait for bus' instruction.
                return new WaitForTransitInstruction(travelerPosition.nextLeg, travelerPosition.currentTime, locale);
            }
            return new OnTrackInstruction(getDistanceToEndOfLeg(travelerPosition), travelerPosition.expectedLeg.to.name, locale);
        }

        Step nextStep = snapToWaypoint(travelerPosition, travelerPosition.expectedLeg.steps);
        if (nextStep != null && (!isPositionPastStep(travelerPosition, nextStep) || isStartOfTrip)) {
            return new OnTrackInstruction(
                getDistance(travelerPosition.currentPosition, new Coordinates(nextStep)),
                nextStep,
                locale
            );
        }
        return null;
    }

    /**
     * Align the traveler's position to the nearest transit stop or destination.
     */
    @Nullable
    public static TripInstruction alignTravelerToTransitTrip(TravelerPosition travelerPosition) {
        Locale locale = travelerPosition.locale;
        Leg expectedLeg = travelerPosition.expectedLeg;
        String finalStop = expectedLeg.to.name;

        if (isApproachingEndOfLeg(travelerPosition)) {
            return new GetOffHereTransitInstruction(finalStop, locale);
        }

        Place nextStop = snapToWaypoint(travelerPosition, getIntermediateAndLastStop(expectedLeg), true);
        if (nextStop != null) {
            int stopsRemaining = stopsUntilEndOfLeg(nextStop, expectedLeg);
            double distance = getDistance(travelerPosition.currentPosition, new Coordinates(nextStop));
            if (stopsRemaining == 1 && distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS && !isPositionPastStep(travelerPosition, nextStop) || stopsRemaining == 0) {
                return new GetOffNextStopTransitInstruction(finalStop, locale);
            } else if (stopsRemaining <= 3) {
                return new GetOffSoonTransitInstruction(finalStop, locale);
            } else if (
                stopsRemaining == expectedLeg.intermediateStops.size() &&
                travelerPosition.speed >= MIN_TRANSIT_VEHICLE_SPEED
            ) {
                return new TransitLegSummaryInstruction(expectedLeg, locale);
            }
        }
        return null;
    }

    /**
     * Check that the current position is not past the "next step". This is to prevent an instruction being provided
     * for a step which is behind the traveler, but is within radius.
     */
    private static boolean isPositionPastStep(TravelerPosition travelerPosition, ConvertsToCoordinates nextStep) {
        double distanceFromPositionToEndOfLegSegment = getDistance(
            travelerPosition.legSegmentFromPosition.end,
            travelerPosition.currentPosition
        );
        double distanceFromStepToEndOfLegSegment = getDistance(
            travelerPosition.legSegmentFromPosition.end,
            nextStep.toCoordinates()
        );
        return distanceFromPositionToEndOfLegSegment < distanceFromStepToEndOfLegSegment;
    }

    /**
     * Is the traveler approaching the leg destination.
     */
    private static boolean isApproachingEndOfLeg(TravelerPosition travelerPosition) {
        return getDistanceToEndOfLeg(travelerPosition) <= TRIP_INSTRUCTION_UPCOMING_RADIUS;
    }

    /**
     * Is the traveler at the leg destination.
     */
    public static boolean isAtEndOfLeg(TravelerPosition travelerPosition) {
        return getDistanceToEndOfLeg(travelerPosition) <= TRIP_INSTRUCTION_IMMEDIATE_RADIUS;
    }

    /**
     * Make sure the traveler is on schedule or ahead of schedule (but not too far) to be within an operational window
     * for the bus service.
     */
    public static boolean isWithinOperationalNotifyWindow(TravelerPosition travelerPosition) {
        var busDepartureTime = getBusDepartureTime(travelerPosition.nextLeg);
        return
            (travelerPosition.currentTime.equals(busDepartureTime) || travelerPosition.currentTime.isBefore(busDepartureTime)) &&
            ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES >= getMinutesAheadOfDeparture(travelerPosition.currentTime, busDepartureTime);
    }

    /**
     * Get how far ahead in minutes the traveler is from the bus departure time.
     */
    public static long getMinutesAheadOfDeparture(Instant currentTime, Instant busDepartureTime) {
        return Duration.between(busDepartureTime, currentTime).toMinutes();
    }

    /**
     * Get the bus departure time.
     */
    public static Instant getBusDepartureTime(Leg busLeg) {
        return ZonedDateTime.ofInstant(
            busLeg.startTime.toInstant().plusSeconds(busLeg.departureDelay),
            DateTimeUtils.getOtpZoneId()
        ).toInstant();
    }

    /**
     * Get the distance from the traveler's current position to the leg destination.
     */
    private static double getDistanceToEndOfLeg(TravelerPosition travelerPosition) {
        Coordinates legDestination = new Coordinates(travelerPosition.expectedLeg.to);
        return getDistance(travelerPosition.currentPosition, legDestination);
    }

    /**
     * From the starting index, find the next waypoint along a leg.
     */
    public static <T extends ConvertsToCoordinates> T getNextWayPoint(List<Coordinates> positions, List<T> steps, int startIndex) {
        Map<T, Coordinates> waypoints = steps
            .stream()
            .collect(Collectors.toMap(s -> s, ConvertsToCoordinates::toCoordinates));

        for (int i = startIndex; i < positions.size(); i++) {
            Coordinates pos = positions.get(i);
            for (var entry : waypoints.entrySet()) {
                if (pos.equals(entry.getValue())) {
                    return entry.getKey();
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

    private static List<Place> getIntermediateAndLastStop(Leg leg) {
        ArrayList<Place> stops = leg.intermediateStops == null
            ? new ArrayList<>()
            : new ArrayList<>(leg.intermediateStops);
        stops.add(leg.to);
        return stops;
    }

    /**
     * Inject waypoints (could be steps on a walk leg, or intermediate stops on a transit leg)
     * into the leg positions. It is assumed that both sets of points are on the same route
     * and are in between the start and end positions. If b = beginning, p = point on leg, W = waypoint and e = end, create
     * a list of coordinates which can be traversed to get the next waypoint.
     * <p>
     * b|p|W|p|p|p|p|p|p|W|p|p|W|p|p|p|p|p|W|e
     */
    public static List<Coordinates> injectWaypointsIntoLegPositions(Leg leg, List<? extends ConvertsToCoordinates> steps) {
        List<Coordinates> allPositions = getAllLegPositions(leg);
        List<Coordinates> waypoints = steps
            .stream()
            .map(ConvertsToCoordinates::toCoordinates)
            .collect(Collectors.toList());
        List<Coordinates> injectedPoints = new ArrayList<>();
        List<Coordinates> finalPositions = new ArrayList<>();
        for (int i = 0; i < allPositions.size() - 1; i++) {
            Coordinates p1 = allPositions.get(i);
            finalPositions.add(p1);
            Coordinates p2 = allPositions.get(i + 1);
            for (Coordinates waypoint : waypoints) {
                if (isPointBetween(p1, p2, waypoint) && !injectedPoints.contains(waypoint)) {
                    finalPositions.add(waypoint);
                    injectedPoints.add(waypoint);
                }
            }
        }

        // Add the destination coords which are missed because of the -1 condition above.
        finalPositions.add(allPositions.get(allPositions.size() - 1));

        if (injectedPoints.size() != waypoints.size()) {
            // One or more waypoints have not been injected because they are not between two geometry points.
            // Inject these based on proximity.
            waypoints
                .stream()
                .filter(pt -> !injectedPoints.contains(pt))
                .forEach(missedPoint -> {
                    int pointIndex = getNearestPointIndex(finalPositions, missedPoint);
                    if (pointIndex != -1) {
                        finalPositions.add(pointIndex, missedPoint);
                    }
                });
        }
        return createExclusionZone(finalPositions, leg);
    }

    /**
     * Align the traveler to the transit leg and provide the next waypoint from this point forward.
     */
    private static <T extends ConvertsToCoordinates> T snapToWaypoint(TravelerPosition pos, List<T> waypoints, boolean excludeCurrent) {
        List<Coordinates> legPositions = injectWaypointsIntoLegPositions(pos.expectedLeg, waypoints);
        int pointIndex = getNearestPointIndex(legPositions, pos.currentPosition);
        int startingIndex = excludeCurrent ? Math.min(pointIndex + 1, legPositions.size() - 1) : pointIndex;
        return pointIndex != -1 ? getNextWayPoint(legPositions, waypoints, startingIndex) : null;
    }

    /**
     * Align the traveler to the transit leg and provide the next waypoint forward, excluding the current position.
     */
    private static <T extends ConvertsToCoordinates> T snapToWaypoint(TravelerPosition pos, List<T> waypoints) {
        return snapToWaypoint(pos, waypoints, false);
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

    /**
     * Create an exclusion zone around a step to remove all geometry points which may skew locating a step on a leg.
     * e.g. On a 90-degree turn, the traveler might be nearer to the point after a step than the step itself resulting
     * in the turn being missed.
     */
    private static List<Coordinates> createExclusionZone(List<Coordinates> positions, Leg leg) {
        List<Coordinates> finalPositions = new ArrayList<>();
        for (Coordinates position : positions) {
            if (isStepPoint(position, leg.steps) || !isWithinExclusionZone(position, leg.steps)) {
                finalPositions.add(position);
            }
        }
        return finalPositions;
    }

    /**
     * Check if the position is attributed to a step.
     */
    private static boolean isStepPoint(Coordinates position, List<Step> steps) {
        for (Step step : steps) {
            if (new Coordinates(step).equals(position)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the position is within the exclusion zone.
     */
    public static boolean isWithinExclusionZone(Coordinates position, List<Step> steps) {
        for (Step step : steps) {
            double distance = getDistance(new Coordinates(step), position);
            if (distance <= TRIP_INSTRUCTION_UPCOMING_RADIUS) {
                return true;
            }
        }
        return false;
    }

    public static int stopsUntilEndOfLeg(Place stop, Leg leg) {
        if (stop == leg.to) return 0;

        List<Place> stops = leg.intermediateStops;
        return stops.size() - stops.indexOf(stop);
    }
}
