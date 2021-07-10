package org.opentripplanner.middleware.tripmonitor.jobs;

import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpTestUtils;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.JourneyState;

import java.time.ZonedDateTime;

class ShouldSkipTripTestCase {
    /**
     * The last time a journey was checked. If this is not set, it is assumed that the trip has never been checked
     * before.
     */
    public ZonedDateTime lastCheckedTime;

    /* a helpful message describing the particular test case */
    public final String message;

    /* The time to mock */
    public final ZonedDateTime mockTime;

    /**
     * if true, it is expected that the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method should
     * calculate that the given trip should be skipped.
     */
    public final boolean shouldSkipTrip;

    /**
     * The trip for the {@link CheckMonitoredTripTest#createSkipTripTestCases()} method to calculate whether
     * skipping trip analysis should occur. If this is not set, then a default weekday trip will be created and
     * used.
     */
    public MonitoredTrip trip;

    ShouldSkipTripTestCase(String message, ZonedDateTime mockTime, boolean shouldSkipTrip) {
        this.message = message;
        this.mockTime = mockTime;
        this.shouldSkipTrip = shouldSkipTrip;
    }

    @Override
    public String toString() {
        return message;
    }

    public CheckMonitoredTrip generateCheckMonitoredTrip(OtpUser user) throws Exception {
        // create a mock OTP response for planning a trip on a weekday target datetime
        OtpResponse mockWeekdayResponse = OtpTestUtils.OTP_DISPATCHER_PLAN_RESPONSE.getResponse();
        Itinerary mockWeekdayItinerary = mockWeekdayResponse.plan.itineraries.get(0);
        OtpTestUtils.updateBaseItineraryTime(
            mockWeekdayItinerary,
            mockTime.withYear(2020).withMonth(6).withDayOfMonth(8).withHour(8).withMinute(40).withSecond(10)
        );

        // create these entries in the database at this point to ensure the correct mocked time is set
        // if trip is null, create the default weekday trip
        if (trip == null) {
            trip = PersistenceTestUtils.createMonitoredTrip(
                user.id,
                OtpTestUtils.OTP_DISPATCHER_PLAN_RESPONSE,
                true,
                OtpTestUtils.createDefaultJourneyState()
            );
        }

        // if last checked time is not null, there is an assumption that the journey state has been created before.
        // Therefore, create a mock journey state and set the matching itinerary to be the mock weekday itinerary.
        // Also, set the journeyState's last checked time to the provided lastCheckedTime.
        if (lastCheckedTime != null) {
            JourneyState journeyState = trip.journeyState;
            journeyState.matchingItinerary = mockWeekdayItinerary;
            journeyState.targetDate = "2020-06-08";
            journeyState.lastCheckedEpochMillis = lastCheckedTime.toInstant().toEpochMilli();
            Persistence.monitoredTrips.replace(trip.id, trip);
        }
        return new CheckMonitoredTrip(trip);
    }
}
