package org.opentripplanner.middleware.tripmonitor.jobs;

class DelayNotificationTestCase {
    /**
     * The trip to use to test. It is assumed that the trip is completely setup with an appropriate journey state.
     */
    public CheckMonitoredTrip checkMonitoredTrip;

    /**
     * Whether the check is for the arrival or departure
     */
    public NotificationType delayType;

    /**
     * The expected body of the notification message. If this is not set, it is assumed in the test case that a
     * notification should not be generated.
     */
    public String expectedNotificationMessage;

    /**
     * Message for test case
     */
    public String message;

    public DelayNotificationTestCase(
        CheckMonitoredTrip checkMonitoredTrip, NotificationType delayType, String message
    ) {
        this(checkMonitoredTrip, delayType, null, message);
    }

    public DelayNotificationTestCase(
        CheckMonitoredTrip checkMonitoredTrip,
        NotificationType delayType,
        String expectedNotificationMessage,
        String message
    ) {
        this.checkMonitoredTrip = checkMonitoredTrip;
        this.delayType = delayType;
        this.expectedNotificationMessage = expectedNotificationMessage;
        this.message = message;
    }
}
