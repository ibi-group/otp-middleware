package org.opentripplanner.middleware.models;

import org.bson.conversions.Bson;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.Response;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static com.mongodb.client.model.Filters.eq;

/**
 * A monitored trip represents a trip a user would like to receive notification on if affected by a delay and/or
 * route change.
 */
public class MonitoredTrip extends Model {

    /**
     * Mongo Id of the {@link OtpUser} monitoring trip.
     */
    public String userId;

    /**
     * Name given to the trip by the user
     */
    public String tripName;

    /**
     * The time at which the trip takes place. This will be in the format HH:mm and is extracted (or provided separately)
     * to the date and time within the query parameters. The reasoning is so that it doesn't have to be extracted every
     * time the trip requires checking.
     * TODO: Remove?
     */
    public String tripTime;

    /**
     * From and to locations for the stored trip.
     */
    public Place from;
    public Place to;

    /**
     * The number of minutes prior to a trip taking place that the status should be checked.
     */
    public int leadTimeInMinutes;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean monday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean tuesday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean wednesday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean thursday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean friday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean saturday;

    /**
     * Specify if the monitored trip should be checked on this day
     */
    public boolean sunday;

    /**
     * Specify if the monitored trip should be checked on a US federal holiday.
     */
    //TODO define US federal holiday source
    public boolean excludeFederalHolidays;

    /**
     * Specify if the monitored trip is active. If true, the trip will be checked.
     */
    public boolean isActive = true;

    /**
     * Query params. Query parameters influencing trip.
     */
    public String queryParams;

    /**
     * The trips itinerary
     */
    public Itinerary itinerary;

    /**
     * Whether to notify the user if an alert is present on monitored trip.
     */
    public boolean notifyOnAlert = true;

    /**
     * Threshold in minutes for a departure time variance (absolute value) to trigger a notification.
     * -1 indicates that notifications are not enabled.
     */
    public int departureVarianceMinutesThreshold = 15;

    /**
     * Threshold in minutes for an arrival time variance (absolute value) to trigger a notification.
     * -1 indicates that notifications are not enabled.
     */
    public int arrivalVarianceMinutesThreshold = 15;

    /**
     * Whether to notify the user if the itinerary details (routes or stops used) change for monitored trip.
     */
    public boolean notifyOnItineraryChange = true;

    public MonitoredTrip() {
    }

    public MonitoredTrip(OtpDispatcherResponse otpDispatcherResponse) {
        queryParams = otpDispatcherResponse.requestUri.getQuery();
        TripPlan plan = otpDispatcherResponse.getResponse().plan;
        itinerary = plan.itineraries.get(0);
        from = plan.from;
        to = plan.to;
        // FIXME: Should we clear any realtime alerts/info here? How to handle trips planned with realtime enabled?
        itinerary.clearAlerts();
        // FIXME: set trip time other params?
//        tripTime = otpDispatcherResponse.response.plan.date
    }

    public MonitoredTrip updateAllDaysOfWeek(boolean value) {
        updateWeekdays(value);
        saturday = value;
        sunday = value;
        return this;
    }

    public MonitoredTrip updateWeekdays(boolean value) {
        monday = value;
        tuesday = value;
        wednesday = value;
        thursday = value;
        friday = value;
        return this;
    }

    public boolean isActiveOnDate(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        // TODO: Maybe we should just refactor DOW to be a list of ints (TIntList).
        return isActive &&
            (
                monday && dayOfWeek == DayOfWeek.MONDAY ||
                tuesday && dayOfWeek == DayOfWeek.TUESDAY ||
                wednesday && dayOfWeek == DayOfWeek.WEDNESDAY ||
                thursday && dayOfWeek == DayOfWeek.THURSDAY ||
                friday && dayOfWeek == DayOfWeek.FRIDAY ||
                saturday && dayOfWeek == DayOfWeek.SATURDAY ||
                sunday && dayOfWeek == DayOfWeek.SUNDAY
            );
    }

    /**
     * Confirm that the requesting user has the required permissions
     */
    @Override
    public boolean canBeManagedBy(Auth0UserProfile user) {
        // If the user is attempting to update someone else's monitored trip, they must be admin.
        boolean belongsToUser = false;

        if (user.otpUser != null) {
            belongsToUser = userId.equals(user.otpUser.id);
        } else if (user.apiUser != null) {
            belongsToUser = userId.equals(user.apiUser.id);
        }

        if (belongsToUser) {
            return true;
        } else if (user.adminUser != null) {
            // If not managing self, user must have manage permission.
            for (Permission permission : user.adminUser.permissions) {
                if (permission.canManage(this.getClass())) return true;
            }
        }
        // Fallback to Model#userCanManage.
        return super.canBeManagedBy(user);
    }

    private Bson tripIdFilter() {
        return eq("monitoredTripId", this.id);
    }

    /**
     * Get the journey state for this trip.
     */
    public JourneyState retrieveJourneyState() {
        JourneyState journeyState = Persistence.journeyStates.getOneFiltered(tripIdFilter());
        if (journeyState == null) journeyState = new JourneyState();
        return journeyState;
    }

    /**
     * Get the latest itinerary that was tracked in the journey state.
     */
    public Itinerary latestItinerary() {
        JourneyState journeyState = retrieveJourneyState();
        if (journeyState.responses.size() > 0) {
            // FIXME: we need to be more intentional about which itinerary we are fetching from the response.
            return journeyState.responses.get(0).plan.itineraries.get(0);
        }
        return itinerary;
    }

    /**
     * Add OTP response to the trip's journey state.
     */
    public void addResponse(Response response) {
        JourneyState journeyState = retrieveJourneyState();
        journeyState.lastChecked = System.currentTimeMillis();
        // FIXME: we may need to be more selective about which itinerary we are storing from the response (e.g., clear
        //  the unused itins).
        journeyState.responses.add(0, response);
        Persistence.journeyStates.replace(journeyState.id, journeyState);
    }

    /**
     * Clear journey state for the trip.
     */
    public boolean clearJourneyState() {
        return Persistence.journeyStates.removeFiltered(tripIdFilter());
    }
}

