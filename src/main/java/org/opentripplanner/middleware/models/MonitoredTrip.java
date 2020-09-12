package org.opentripplanner.middleware.models;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getZoneIdForCoordinates;

/**
 * A monitored trip represents a trip a user would like to receive notification on if affected by a delay and/or route
 * change.
 */
public class MonitoredTrip extends Model {

    /**
     * Mongo Id of the {@link OtpUser} who owns this monitored trip.
     */
    public String userId;

    /**
     * Name given to the trip by the user
     */
    public String tripName;

    /**
     * The time at which the trip takes place. This will be in the format HH:mm and is extracted (or provided
     * separately) to the date and time within the query parameters. The reasoning is so that it doesn't have to be
     * extracted every time the trip requires checking.
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
     * -1 indicates that a change in the departure time will not trigger a notification.
     */
    public int departureVarianceMinutesThreshold = 15;

    /**
     * Threshold in minutes for an arrival time variance (absolute value) to trigger a notification.
     * -1 indicates that a change in the arrival time will not trigger a notification.
     */
    public int arrivalVarianceMinutesThreshold = 15;

    /**
     * Whether to notify the user if the itinerary details (routes or stops used) change for monitored trip.
     */
    public boolean notifyOnItineraryChange = true;

    private transient JourneyState journeyState;

    private transient List<NameValuePair> parsedParams;

    public MonitoredTrip() {
    }

    public MonitoredTrip(OtpDispatcherResponse otpDispatcherResponse) {
        queryParams = otpDispatcherResponse.requestUri.getQuery();
        TripPlan plan = otpDispatcherResponse.getResponse().plan;
        itinerary = plan.itineraries.get(0);
        initializeFromItinerary();
    }

    public void initializeFromItinerary() {
        int lastLegIndex = itinerary.legs.size() - 1;
        from = itinerary.legs.get(0).from;
        to = itinerary.legs.get(lastLegIndex).to;
        // Ensure the itinerary we store does not contain any realtime info.
        clearRealtimeInfo();
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

    /**
     * Returns true if the trip is not active overall or if all days of the week are set to false
     */
    public boolean isInActive() {
        return !isActive || (
          !monday && !tuesday && !wednesday && !thursday && !friday && !saturday && !sunday
        );
    }

    public boolean isActiveOnDate(ZonedDateTime zonedDateTime) {
        DayOfWeek dayOfWeek = zonedDateTime.getDayOfWeek();
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

    @Override
    public boolean canBeCreatedBy(Auth0UserProfile profile) {
        OtpUser otpUser = profile.otpUser;
        if (userId == null) {
            if (otpUser == null) {
                // The otpUser must exist (and be the requester) if the userId is null. Otherwise, there is nobody to
                // assign the trip to.
                return false;
            }
            // If userId on trip is null, auto-assign the otpUser's id to trip.
            userId = otpUser.id;
        } else {
            // If userId was provided, follow authorization provided by canBeManagedBy
            return canBeManagedBy(profile);
        }
        return super.canBeCreatedBy(profile);
    }

    /**
     * Confirm that the requesting user has the required permissions
     */
    @Override
    public boolean canBeManagedBy(Auth0UserProfile user) {
        // This should not be possible, but return false on a null userId just in case.
        if (userId == null) return false;
        // If the user is attempting to update someone else's monitored trip, they must be admin.
        boolean belongsToUser = false;
        // Monitored trip can only be owned by an OtpUser (not an ApiUser or AdminUser).
        if (user.otpUser != null) {
            belongsToUser = userId.equals(user.otpUser.id);
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
        // first return the journeyState for this trip if it has already been fetched
        if (journeyState != null) return journeyState;
        // hasn't been fetched, attempt to retrieve from the db
        journeyState = Persistence.journeyStates.getOneFiltered(tripIdFilter());
        // If journey state does not exist, create and persist.
        if (journeyState == null) {
            journeyState = new JourneyState(this);
            Persistence.journeyStates.create(journeyState);
        }
        return journeyState;
    }

    /**
     * Get the latest itinerary that was tracked in the journey state or null if the check has never been performed (or
     * a matching itinerary has never been found).
     */
    public Itinerary latestItinerary() {
        JourneyState journeyState = retrieveJourneyState();
        return journeyState.matchingItinerary;
    }

    /**
     * Clear journey state for the trip. TODO: remove?
     */
    public boolean clearJourneyState() {
        return Persistence.journeyStates.removeFiltered(tripIdFilter());
    }

    /**
     * Clear real-time info from itinerary to store.
     * FIXME: Do we need to clear more than the alerts?
     */
    public void clearRealtimeInfo() {
        itinerary.clearAlerts();
    }

    /**
     * Get monitored trips for the specified {@link OtpUser} user Id.
     */
    public static List<MonitoredTrip> tripsForUser(String userId) {
        return Persistence.monitoredTrips.getFiltered(TypedPersistence.filterByUserId(userId));
    }

    @Override
    public boolean delete() {
        // TODO: Add journey state deletion.
        return Persistence.monitoredTrips.removeById(this.id);
    }

    public List<NameValuePair> getParsedParams() throws URISyntaxException {
        // use the transient value of parsedParams if it is available
        if (parsedParams != null) return parsedParams;

        // need to parse the params
        parsedParams = URLEncodedUtils.parse(
            new URI(String.format("http://example.com/%s", queryParams)),
            UTF_8
        );
        return parsedParams;
    }

    public boolean isArriveBy() throws URISyntaxException {
        for (NameValuePair param : parsedParams) {
            if (param.getName().equals("arriveBy")) {
                return param.getValue().equals("true");
            }
        }

        // if arriveBy is not included in query params, OTP will default to false, so initialize to false
        return false;
    }

    /**
     * Gets the timezone of the target location. When planning a trip, we either want to depart at a certain time or 
     * arrive by a certain time. When departing, we use the local time present at the origin and when arriving by we 
     * use the local time at the destination. Therefore, this method will return the timezone at the destination if this
     * trip is an arriveBy trip, or the timezone at the origin if the trip is a depart at trip.
     */
    public ZoneId getTimezoneForTargetLocation() throws URISyntaxException {
        double lat, lon;
        if (isArriveBy()) {
            lat = to.lat;
            lon = to.lon;
        } else {
            lat = from.lat;
            lon = from.lon;
        }
        Optional<ZoneId> fromZoneId = getZoneIdForCoordinates(lat, lon);
        if (fromZoneId.isEmpty()) {
            String message = String.format(
                "Could not find coordinate's (lat=%.6f, lon=%.6f) timezone for monitored trip %s",
                lat,
                lon,
                id
            );
            throw new RuntimeException(message);
        } 
        return fromZoneId.get();
    }

    /**
     * Returns the target hour of the day that the trip is either departing at or arriving by
     */
    public int getHour() {
        return Integer.valueOf(tripTime.split(":")[0]);
    }

    /**
     * Returns the target minute of the hour that the trip is either departing at or arriving by
     */
    public int getMinute() {
        return Integer.valueOf(tripTime.split(":")[1]);
    }
}

