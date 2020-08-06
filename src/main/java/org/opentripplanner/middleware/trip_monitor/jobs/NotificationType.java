package org.opentripplanner.middleware.trip_monitor.jobs;

import org.opentripplanner.middleware.models.TripMonitorNotification;

/**
 * Contains the various types of {@link TripMonitorNotification} that can be sent during {@link CheckMonitoredTrip}.
 */
public enum NotificationType {
    DEPARTURE_DELAY,
    ARRIVAL_DELAY,
    ITINERARY_CHANGED, // TODO
    ALERT_FOUND,
    ITINERARY_NOT_FOUND
}