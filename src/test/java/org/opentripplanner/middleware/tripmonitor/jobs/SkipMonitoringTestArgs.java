package org.opentripplanner.middleware.tripmonitor.jobs;

class SkipMonitoringTestArgs {
    /** Offset to trip start, in seconds */
    public final int tripStartOffsetSecs;
    /** Offset to trip end, in seconds */
    public final int tripEndOffsetSecs;
    /** Whether the trip is one-time or recurring */
    public final boolean isRecurring;
    /* A helpful message describing the particular test case */
    public final String message;

    /**
     * If true, it is expected that the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method should
     * calculate that the given trip should be skipped.
     */
    public final boolean result;

    public final boolean withJourneyState;

    public SkipMonitoringTestArgs(
        int tripStartOffsetSecs,
        int tripEndOffsetSecs,
        boolean isRecurring,
        boolean withJourneyState,
        boolean result,
        String message
    ) {
        this.tripStartOffsetSecs = tripStartOffsetSecs;
        this.tripEndOffsetSecs = tripEndOffsetSecs;
        this.isRecurring = isRecurring;
        this.withJourneyState = withJourneyState;
        this.message = message;
        this.result = result;
    }

    public SkipMonitoringTestArgs(
        int tripStartOffsetSecs,
        int tripEndOffsetSecs,
        boolean isRecurring,
        boolean result,
        String message
    ) {
        this(tripStartOffsetSecs, tripEndOffsetSecs, isRecurring, false, result, message);
    }
}
