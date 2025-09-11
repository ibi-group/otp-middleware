package org.opentripplanner.middleware.triptracker;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.leonard.PolylineUtils;
import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Step;
import org.opentripplanner.middleware.triptracker.instruction.ContinueInstruction;
import org.opentripplanner.middleware.triptracker.instruction.ContinueRidingTransitInstruction;
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
import org.opentripplanner.middleware.utils.ItineraryUtils;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    public static final int MIN_TRANSIT_VEHICLE_SPEED = 5; // meters per second. 11.1 mph or 18 km/h.

    private TravelerLocator() {
    }

    /**
     * Define the instruction based on the traveler's current position compared to expected and nearest points on the
     * trip.
     */
    public static TripInstruction getInstruction(TripStatus tripStatus, TravelerPosition travelerPosition) {
        if (hasRequiredWalkLeg(travelerPosition)) {
            if (hasRequiredTripStatus(tripStatus)) {
                TripInstruction tripInstruction = alignTravelerToTrip(travelerPosition, false);
                if (tripInstruction != null) return tripInstruction;
            }

            if (tripStatus.equals(TripStatus.DEVIATED)) {
                TripInstruction tripInstruction = getBackOnTrack(travelerPosition);
                if (tripInstruction != null) return tripInstruction;
            }
        } else if (hasRequiredTransitLeg(travelerPosition)) {
            if (hasRequiredTripStatus(tripStatus)) {
                TripInstruction tripInstruction = alignTravelerToTransitTrip(travelerPosition);
                if (tripInstruction != null) return tripInstruction;
            }

            if (tripStatus.equals(TripStatus.DEVIATED)) {
                TripInstruction tripInstruction = getBackOnTrack(travelerPosition);
                if (tripInstruction != null) return tripInstruction;
            }
        }
        return null;
    }

    /**
     * Has required walk leg.
     */
    public static boolean hasRequiredWalkLeg(TravelerPosition travelerPosition) {
        return
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.mode.equalsIgnoreCase("walk");
    }

    /**
     * Has required transit leg.
     */
    public static boolean hasRequiredTransitLeg(TravelerPosition travelerPosition) {
        return
            travelerPosition.expectedLeg != null &&
            travelerPosition.expectedLeg.transitLeg;
    }

    /**
     * The trip instruction can only be provided if the traveler is close to the indicated route.
     */
    public static boolean hasRequiredTripStatus(TripStatus tripStatus) {
        return !tripStatus.equals(TripStatus.DEVIATED) && !tripStatus.equals(TripStatus.ENDED);
    }

    /**
     * Attempt to align the deviated traveler to the trip when on access legs (e.g. walk legs).
     * If the traveler happens to be within an upcoming instruction, the instruction will be issued,
     * else suggest the closest street to head towards.
     */
    @Nullable
    private static TripInstruction getBackOnTrack(TravelerPosition travelerPosition) {
        TripInstruction instruction = alignTravelerToTrip(travelerPosition, true);
        if (instruction != null && instruction.hasInstruction()) {
            return instruction;
        }
        return getDeviatedInstruction(travelerPosition);
    }

    /**
     * If the traveler has deviated, attempt to provide instructions to get back on track.
     */
    @Nullable
    private static TripInstruction getDeviatedInstruction(TravelerPosition travelerPosition) {
        if (!isBusLeg(travelerPosition.expectedLeg)) {
            Step nearestStep = snapToWaypoint(travelerPosition, travelerPosition.expectedLeg.steps);
            return (nearestStep != null && !nearestStep.isEndOfRouting())
                ? new DeviatedInstruction(nearestStep.streetName, travelerPosition.locale)
                : null;
        } else if (atStartOfTransitLeg(travelerPosition)) {
            // Only provide instruction if at the start of a trip.
            String busStopName = getBusStopName(travelerPosition.expectedLeg);
            return (busStopName != null)
                ? new DeviatedInstruction(busStopName, travelerPosition.locale)
                : null;
        }
        return null;
    }

    /**
     * Get the bus stop name from the 'from' place name if available.
     */
    @Nullable
    private static String getBusStopName(Leg busLeg) {
        return Optional
            .ofNullable(busLeg)
            .map(leg -> leg.from)
            .map(place -> place.name)
            .orElse(null);
    }

    /**
     * Align the traveler's position to the nearest step or destination.
     */
    @Nullable
    public static TripInstruction alignTravelerToTrip(
        TravelerPosition travelerPosition,
        boolean travelerHasDeviated
    ) {
        Locale locale = travelerPosition.locale;

        if (isApproachingEndOfLeg(travelerPosition)) {
            Leg transitLeg = travelerPosition.getTransitLegWithClosestUpcomingOrigin();
            if (transitLeg != null) {
                if (shouldSendBusNotification(transitLeg, travelerPosition.currentTime)) {
                    sendBusNotifications(travelerPosition, transitLeg);
                }
                // Regardless of whether the notification is sent or qualifies, provide a 'wait for bus' instruction.
                return new WaitForTransitInstruction(transitLeg, travelerPosition.currentTime, locale);
            }

            Step nextStep = snapToWaypoint(travelerPosition, travelerPosition.expectedLeg.steps);
            if (nextStep != null && nextStep.isEndOfRouting()) {
                return new OnTrackInstruction(
                    getDistance(travelerPosition.currentPosition, new Coordinates(nextStep)),
                    nextStep,
                    locale
                );
            }

            // At this point, the traveler could be approaching the leg's destination
            // or the end of routing, if the trip's final destination is away from the street network.
            List<Coordinates> legPositions = travelerPosition.getLegPositions();
            Coordinates lastShapeCoordinate = legPositions.get(legPositions.size() - 2);
            double distanceToLastShapeCoords = getDistance(travelerPosition.currentPosition, lastShapeCoordinate);

            Coordinates legDestination = new Coordinates(travelerPosition.expectedLeg.to);
            double distanceToLegDestination = getDistance(travelerPosition.currentPosition, legDestination);

            if (distanceToLastShapeCoords < distanceToLegDestination) {
                // Issue a leg end-of-routing step
                Step endOfRoutingStep = createEndOfRoutingStep(
                    legPositions,
                    ItineraryUtils.isBusLeg(travelerPosition.nextLeg) ? "bus stop" : "destination"
                );
                return new OnTrackInstruction(
                    getDistance(travelerPosition.currentPosition, new Coordinates(endOfRoutingStep)),
                    endOfRoutingStep,
                    locale
                );
            }
            return new OnTrackInstruction(distanceToLegDestination, travelerPosition.expectedLeg.to.name, locale);
        }

        Step nextStep = snapToWaypoint(travelerPosition, travelerPosition.expectedLeg.steps, false);
        TripInstruction tripInstruction = null;
        if (nextStep != null && (!isPositionPastStep(travelerPosition, nextStep))) {
            tripInstruction = new OnTrackInstruction(
                getDistance(travelerPosition.currentPosition, new Coordinates(nextStep)),
                nextStep,
                locale
            );
        }
        return (travelerHasDeviated || (tripInstruction != null && tripInstruction.hasInstruction()))
            ? tripInstruction
            : getContinueInstruction(travelerPosition, nextStep, locale);
    }

    /**
     * Traveler is on track, but no immediate instruction is available. Provide a "continue on street" reassurance
     * instruction if the traveler is on a walk leg. This will be based on the next or previous step depending on the
     * traveler's relative position to both.
     */
    private static ContinueInstruction getContinueInstruction(
        TravelerPosition travelerPosition,
        Step nextStep,
        Locale locale
    ) {
        List<Step> steps = travelerPosition.expectedLeg.steps;
        if (Boolean.TRUE.equals(!travelerPosition.expectedLeg.transitLeg) && steps != null && !steps.isEmpty()) {
            Step previousStep = getPreviousStep(steps, nextStep);
            if (previousStep != null) {
                boolean travelerBetweenSteps = nextStep == null || isPointBetween(
                    previousStep.toCoordinates(),
                    nextStep.toCoordinates(),
                    travelerPosition.currentPosition
                );
                if (travelerBetweenSteps) {
                    return new ContinueInstruction(previousStep, locale);
                } else if (isWithinStepRange(travelerPosition, previousStep)) {
                    return new ContinueInstruction(previousStep, locale);
                } else if (isWithinStepRange(travelerPosition, nextStep)) {
                    return new ContinueInstruction(nextStep, locale);
                }
            } else if (nextStep != null) {
                return new ContinueInstruction(nextStep, locale);
            }
        }
        return null;
    }

    /**
     * The traveler is still with the provided step range.
     */
    private static boolean isWithinStepRange(TravelerPosition travelerPosition, Step step) {
        if (step.isEndOfRouting()) return true;

        double distanceFromTravelerToStep = getDistance(travelerPosition.currentPosition, step.toCoordinates());
        return distanceFromTravelerToStep < step.distance;
    }

    /**
     * Get the step prior to the next step provided.
     */
    private static Step getPreviousStep(List<Step> steps, Step nextStep) {
        if (steps.get(0).equals(nextStep)) {
            return null;
        }
        Optional<Step> previousStep = IntStream
            .range(0, steps.size())
            .filter(i -> steps.get(i).equals(nextStep))
            .mapToObj(i -> steps.get(i - 1))
            .findFirst();
        return previousStep.orElse(steps.get(steps.size() - 1));
    }

    /**
     * Whether to send a bus notification if the first leg is a bus leg or approaching a bus leg and within the notify window.
     */
    public static boolean shouldSendBusNotification(Leg leg, Instant currentTime) {
        return (isBusLeg(leg) && isWithinOperationalNotifyWindow(currentTime, leg));
    }

    /**
     * Send bus notification for the corresponding leg.
     */
    private static void sendBusNotifications(TravelerPosition travelerPosition, Leg busLeg) {
        BusOperatorActions
            .getDefault()
            .handleSendNotificationAction(travelerPosition, busLeg);
    }

    /**
     * Determines whether the traveler is at the beginning of a transit leg,
     * i.e. is near the departing stop.
     */
    private static boolean atStartOfTransitLeg(TravelerPosition travelerPosition) {
        Leg expectedLeg = travelerPosition.expectedLeg;
        if (expectedLeg == null || !expectedLeg.transitLeg) return false;

        Place nextStop = snapToWaypoint(travelerPosition, getIntermediateAndLastStop(expectedLeg), true);
        int stopsRemaining = stopsUntilEndOfLeg(nextStop, expectedLeg);
        return stopsRemaining == expectedLeg.intermediateStops.size();
    }

    /**
     * Align the traveler's position to the nearest transit stop or destination.
     */
    @Nullable
    public static TripInstruction alignTravelerToTransitTrip(TravelerPosition travelerPosition) {
        Locale locale = travelerPosition.locale;
        Leg expectedLeg = travelerPosition.expectedLeg;
        String finalStop = expectedLeg.to.name;

        Leg transitLeg = travelerPosition.getTransitLegWithClosestUpcomingOrigin();
        if (transitLeg != null) {
            if (shouldSendBusNotification(transitLeg, travelerPosition.currentTime)) {
                sendBusNotifications(travelerPosition, transitLeg);
            }
            // Regardless of whether the notification is sent or qualifies, provide a 'wait for bus' instruction.
            return new WaitForTransitInstruction(transitLeg, travelerPosition.currentTime, locale);
        }

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
                // When on board, after the transit vehicle departs the boarding stop, announce how long to ride
                // (similar to the itinerary narrative in OTP-react-redux),
                return new TransitLegSummaryInstruction(expectedLeg, locale);
            } else {
                // While far from the exiting stop, simply announce "Continue riding the bus."
                return new ContinueRidingTransitInstruction();
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
    public static boolean isApproachingEndOfLeg(TravelerPosition travelerPosition) {
        return getDistanceToEndOfLeg(travelerPosition) <= TRIP_INSTRUCTION_UPCOMING_RADIUS;
    }

    /**
     * Is the traveler at the start of a leg.
     */
    public static boolean isAtStartOfLeg(TravelerPosition travelerPosition) {
        return getDistanceToStartOfLeg(travelerPosition) <= TRIP_INSTRUCTION_UPCOMING_RADIUS;
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
    public static boolean isWithinOperationalNotifyWindow(Instant currentTime, Leg busLeg) {
        var busDepartureTime = getBusDepartureTime(busLeg);
        return
            (currentTime.equals(busDepartureTime) || currentTime.isBefore(busDepartureTime)) &&
            ACCEPTABLE_AHEAD_OF_SCHEDULE_IN_MINUTES >= getMinutesAheadOfDeparture(currentTime, busDepartureTime);
    }

    /**
     * Get how far ahead in minutes the traveler is from the bus departure time.
     */
    public static long getMinutesAheadOfDeparture(Instant currentTime, Instant busDepartureTime) {
        return Duration.between(currentTime, busDepartureTime).toMinutes();
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

    private static double getDistanceToStartOfLeg(TravelerPosition travelerPosition) {
        return getDistanceToStartOfLeg(travelerPosition, travelerPosition.getLegPositions());
    }

    /**
     * Get the distance from the traveler's current position to the leg destination from given leg positions.
     */
    public static double getDistanceToStartOfLeg(TravelerPosition travelerPosition, List<Coordinates> legPositions) {
        Coordinates secondCoordinate = legPositions.get(1);
        Coordinates firstCoordinate = legPositions.get(0);
        Coordinates legOrigin = new Coordinates(travelerPosition.expectedLeg.from);

        // If the first leg position coordinate is identical to the leg origin,
        // it probably means the origin is off the street network, so the first shape coordinate is at pos (1).
        // If the first leg position coordinate differs from the leg origin,
        // then the origin is probably on the street network, so the first shape coordinate is at pos (0).
        double distanceToFirstShapeCoords = getDistance(
            travelerPosition.currentPosition,
            firstCoordinate.equals(legOrigin) ? secondCoordinate : firstCoordinate
        );

        double distanceToLegOrigin = getDistance(travelerPosition.currentPosition, legOrigin);

        return Math.min(distanceToFirstShapeCoords, distanceToLegOrigin);
    }

    /**
     * Get the distance from the traveler's current position to the leg destination.
     */
    private static double getDistanceToEndOfLeg(TravelerPosition travelerPosition) {
        return getDistanceToEndOfLeg(travelerPosition, travelerPosition.getLegPositions());
    }

    /**
     * Get the distance from the traveler's current position to the leg destination from given leg positions.
     * This method is used in tests when the leg positions are computed using an 'upcoming' threshold
     * different from the default one.
     */
    public static double getDistanceToEndOfLeg(TravelerPosition travelerPosition, List<Coordinates> legPositions) {
        Coordinates secondToLastCoordinate = legPositions.get(legPositions.size() - 2);
        Coordinates lastCoordinate = legPositions.get(legPositions.size() - 1);
        Coordinates legDestination = new Coordinates(travelerPosition.expectedLeg.to);

        // HACK:
        // If the last leg position coordinate is identical to the leg destination,
        // it probably means the destination is off the street network, so the last shape coordinate is at pos (size -2).
        // If the last leg position coordinate differs from the leg destination,
        // then the destination is probably on the street network, so the last shape coordinate is at pos (size - 1).
        double distanceToLastShapeCoords = getDistance(
            travelerPosition.currentPosition,
            lastCoordinate.equals(legDestination) ? secondToLastCoordinate : lastCoordinate
        );

        double distanceToLegDestination = getDistance(travelerPosition.currentPosition, legDestination);

        return Math.min(distanceToLastShapeCoords, distanceToLegDestination);
    }

    /**
     * From the starting index, find the next waypoint along a leg.
     * If no waypoint has been found, try the previous positions (result much less likely to be null).
     */
    public static <T extends ConvertsToCoordinates> T getNextOrClosestWayPoint(List<Coordinates> positions, List<T> steps, int startIndex) {
        Map<T, Coordinates> waypoints = steps
            .stream()
            .collect(Collectors.toMap(s -> s, ConvertsToCoordinates::toCoordinates));

        // Look in the next waypoints first, starting from startIndex.
        List<Coordinates> initialPositions = positions.subList(startIndex, positions.size());
        // Fallback from the position before startIndex, going back to the first waypoint.
        List<Coordinates> fallbackPositions = Lists.reverse(positions.subList(0, startIndex));

        for (Coordinates position : Iterables.concat(initialPositions, fallbackPositions)) {
            for (var entry : waypoints.entrySet()) {
                if (position.equals(entry.getValue())) {
                    T waypoint = entry.getKey();
                    if (waypoint != null) return waypoint;
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
    public static List<Coordinates> injectWaypointsIntoLegPositions(Leg leg, List<? extends ConvertsToCoordinates> steps, int exclusionRadius) {
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
        return createExclusionZone(finalPositions, leg, exclusionRadius);
    }

    /**
     * Align the traveler to the transit leg and provide the next waypoint from this point forward.
     */
    private static <T extends ConvertsToCoordinates> T snapToWaypoint(TravelerPosition pos, List<T> waypoints, boolean excludeCurrent) {
        List<Coordinates> legPositions = injectWaypointsIntoLegPositions(pos.expectedLeg, waypoints, TRIP_INSTRUCTION_UPCOMING_RADIUS);
        int pointIndex = getNearestPointIndex(legPositions, pos.currentPosition);
        int startingIndex = excludeCurrent ? Math.min(pointIndex + 1, legPositions.size() - 1) : pointIndex;
        return pointIndex != -1 ? getNextOrClosestWayPoint(legPositions, waypoints, startingIndex) : null;
    }

    /**
     * Creates a special end-of-routing step to instruct that no further routing instructions are available.
     */
    private static Step createEndOfRoutingStep(List<Coordinates> legPositions, String locationName) {
        Coordinates lastShapeCoordinate = legPositions.get(legPositions.size() - 2);
        Step step = new Step();
        step.lat = lastShapeCoordinate.lat;
        step.lon = lastShapeCoordinate.lon;
        step.relativeDirection = Step.END_OF_ROUTING;
        step.streetName = locationName;
        return step;
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
    private static List<Coordinates> createExclusionZone(List<Coordinates> positions, Leg leg, int radius) {
        List<Coordinates> finalPositions = new ArrayList<>();
        int index = 0;
        for (Coordinates position : positions) {
            // Include the last coordinate (at positions[size - 2]; size - 1 is the 'to' location)
            // in case the destination on the final walk leg of an itinerary is far (outside the "immediate" radius)
            // of the last coordinate of the routing shape.
            // That coordinate is inserted second to last, to keep the 'to' location as last.
            if (isStepPoint(position, leg.steps) || !isWithinExclusionZone(position, leg.steps, radius) || index == positions.size() - 2) {
                finalPositions.add(position);
            }
            index++;
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
    public static boolean isWithinExclusionZone(Coordinates position, List<Step> steps, int radius) {
        for (Step step : steps) {
            double distance = getDistance(new Coordinates(step), position);
            if (distance <= radius) {
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
