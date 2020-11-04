package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import com.mongodb.client.FindIterable;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.opentripplanner.middleware.utils.ItineraryUtils.DATE_PARAM;
import static org.opentripplanner.middleware.utils.ItineraryUtils.TIME_PARAM;

/**
 * A monitored trip represents a trip a user would like to receive notification on if affected by a delay and/or route
 * change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
     * The trip's itinerary
     */
    public org.opentripplanner.middleware.otp.response.Itinerary itinerary;

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

    public ItineraryExistence itineraryExistence;

    public MonitoredTrip() {
    }

    public MonitoredTrip(OtpDispatcherResponse otpDispatcherResponse) throws URISyntaxException {
        queryParams = otpDispatcherResponse.requestUri.getQuery();
        TripPlan plan = otpDispatcherResponse.getResponse().plan;
        itinerary = plan.itineraries.get(0);

        // extract trip time from parsed params and itinerary
        initializeFromItineraryAndQueryParams();
    }

    /**
     * Checks that, for each query provided, an itinerary exists.
     * @param checkAllDays Determines whether all days of the week are checked,
     *                     or just the days the trip is set to be monitored.
     * @return a summary of the itinerary existence results for each day of the week
     */
    public boolean checkItineraryExistence(boolean checkAllDays, boolean replaceItinerary) throws URISyntaxException {
        // Get queries to execute by date.
        List<OtpRequest> queriesByDate = getItineraryExistenceQueries(checkAllDays);
        this.itineraryExistence = new ItineraryExistence(queriesByDate, this.itinerary);
        this.itineraryExistence.checkExistence();
        boolean itineraryExists = this.itineraryExistence.allCheckedDaysAreValid();
        // If itinerary should be replaced, do so if all checked days are valid.
        return replaceItinerary && itineraryExists
            ? this.updateTripWithVerifiedItinerary()
            : itineraryExists;
    }

    /**
     * Replace the itinerary provided with the monitored trip
     * with a non-real-time, verified itinerary from the responses provided.
     */
    private boolean updateTripWithVerifiedItinerary() throws URISyntaxException {
        Map<String, String> params = parseQueryParams();
        String queryDate = params.get(DATE_PARAM);

        // TODO: Refactor. Originally found in ItineraryUtils#getDatesToCheckItineraryExistence.
        DayOfWeek dayOfWeek = DateTimeUtils.getDateFromQueryDateString(queryDate).getDayOfWeek();

        // Find the response corresponding to the day of the query.
        // TODO/FIXME: There is a possibility that the user chooses to monitor the query/trip provided
        //       on other days but not the day for which the plan request was originally made.
        //       In such cases, the actual itinerary can be different from the one we are looking to save.
        //       To address that, in the UI, we can, for instance, force the date for the plan request to be monitored.
        Itinerary verifiedItinerary = this.itineraryExistence.getItineraryForDayOfWeek(dayOfWeek);
        if (verifiedItinerary != null) {
            // Set itinerary for monitored trip if verified itinerary is available.
            this.itinerary = verifiedItinerary;
            this.initializeFromItineraryAndQueryParams();
            return true;
        } else {
            // Otherwise, set itinerary existence error/message.
            this.itineraryExistence.error = true;
            this.itineraryExistence.message = String.format("No verified itinerary found for date: %s.", queryDate);
            return false;
        }
    }

    /**
     * Gets OTP queries to check non-realtime itinerary existence for the given trip.
     */
    @JsonIgnore
    @BsonIgnore
    public List<OtpRequest> getItineraryExistenceQueries(boolean checkAllDays)
        throws URISyntaxException {
        return ItineraryUtils.getOtpRequestsForDates(
            ItineraryUtils.excludeRealtime(parseQueryParams()),
            ItineraryUtils.getDatesToCheckItineraryExistence(this, checkAllDays)
        );
    }

    /**
     * Initializes a MonitoredTrip by deriving some fields from the currently set itinerary. Also, the realtime info of
     * the itinerary is removed.
     */
    public void initializeFromItineraryAndQueryParams() throws IllegalArgumentException, URISyntaxException {
        int lastLegIndex = itinerary.legs.size() - 1;
        from = itinerary.legs.get(0).from;
        to = itinerary.legs.get(lastLegIndex).to;

        // Ensure the itinerary we store does not contain any realtime info.
        clearRealtimeInfo();

        // set the trip time by parsing the query params
        tripTime = this.parseQueryParams().get(TIME_PARAM);
        if (tripTime == null) {
            throw new IllegalArgumentException("A monitored trip must have a time set in the query params!");
        }
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
    public boolean isInactive() {
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
        // attempt to retrieve from the db
        JourneyState journeyState = Persistence.journeyStates.getOneFiltered(tripIdFilter());
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
    public org.opentripplanner.middleware.otp.response.Itinerary latestItinerary() {
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
    public static FindIterable<MonitoredTrip> tripsForUser(String userId) {
        return Persistence.monitoredTrips.getFiltered(TypedPersistence.filterByUserId(userId));
    }

    @Override
    public boolean delete() {
        // TODO: Add journey state deletion.
        return Persistence.monitoredTrips.removeById(this.id);
    }

    /**
     * Parse the query params for this trip into a map of the variables.
     */
    public Map<String, String> parseQueryParams() throws URISyntaxException {
        return URLEncodedUtils.parse(
            new URI(String.format("http://example.com/plan?%s", queryParams)),
            UTF_8
        ).stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    }

    /**
     * Check if the trip is planned with the target time being an arriveBy or departAt query.
     *
     * @return true, if the trip's target time is for an arriveBy query
     */
    public boolean isArriveBy() throws URISyntaxException {
        // if arriveBy is not included in query params, OTP will default to false, so initialize to false
        return parseQueryParams().getOrDefault("arriveBy", "false").equals("true");
    }

    /**
     * Returns the target hour of the day that the trip is either departing at or arriving by
     */
    public int tripTimeHour() {
        return Integer.valueOf(tripTime.split(":")[0]);
    }

    /**
     * Returns the trip time as a {@link ZonedDateTime} given a particular date.
     */
    public ZonedDateTime tripZonedDateTime(LocalDate date) {
        return ZonedDateTime.of(
            date, LocalTime.of(tripTimeHour(), tripTimeMinute()), DateTimeUtils.getOtpZoneId()
        );
    }

    /**
     * Returns the target minute of the hour that the trip is either departing at or arriving by
     */
    public int tripTimeMinute() {
        return Integer.valueOf(tripTime.split(":")[1]);
    }
}

