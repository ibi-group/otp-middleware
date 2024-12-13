package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.models.TripMonitorNotification;

/**
 * Contains the various types of {@link TripMonitorNotification} that can be sent during {@link CheckMonitoredTrip}.
 */
public enum NotificationType {
    DEPARTURE_DELAY,
    ARRIVAL_DELAY,
    ITINERARY_CHANGED, // TODO
    ALERT_FOUND,
    ITINERARY_NOT_FOUND,
    INITIAL_REMINDER,
    ARRIVED_AND_MODE_CHANGE_NOTIFICATION,
    DEPARTED_NOTIFICATION,
    ARRIVED_NOTIFICATION
}