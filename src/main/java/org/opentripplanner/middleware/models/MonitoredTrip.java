package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mongodb.client.FindIterable;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.auth.RequestingUser;
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
<<<<<<< Updated upstream
import java.util.Set;
=======
>>>>>>> Stashed changes
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

    public JourneyState journeyState;


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

    /**
     * Records the last itinerary existence check results for this trip.
     */
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
        Map<String, String> parsedQueryParams = parseQueryParams();

        // Update modes in query params with set derived from itinerary. This ensures that, when sending requests to OTP
        // for trip monitoring, we are querying the modes retained by the user when they saved the itinerary.
        Set<String> modes = ItineraryUtils.deriveModesFromItinerary(itinerary);
        parsedQueryParams.put(MODE_PARAM, String.join(",", modes));
        this.queryParams = ItineraryUtils.toQueryString(parsedQueryParams);

        // Ensure the itinerary we store does not contain any realtime info.
        clearRealtimeInfo();

        // set the trip time by parsing the query params
        tripTime = parsedQueryParams.get(TIME_PARAM);
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
    public boolean canBeCreatedBy(RequestingUser requestingUser) {
        if (userId == null) {
            OtpUser otpUser = requestingUser.otpUser;
            if (otpUser == null) {
                // The otpUser must exist (and be the requester) if the userId is null. Otherwise, there is nobody to
                // assign the trip to.
                return false;
            }
            // If userId on trip is null, auto-assign the otpUser's id to trip.
            userId = otpUser.id;
        } else {
            // If userId was provided, follow authorization provided by canBeManagedBy
            return canBeManagedBy(requestingUser);
        }
        return super.canBeCreatedBy(requestingUser);
    }

    /**
     * Confirm that the requesting user has the required permissions
     */
    @Override
    public boolean canBeManagedBy(RequestingUser requestingUser) {
        // This should not be possible, but return false on a null userId just in case.
        if (userId == null) return false;
        // If the user is attempting to update someone else's monitored trip, they must be admin or an API user if the
        // OTP user is assigned to that API.
        boolean belongsToUser = false;
        // Monitored trip can only be owned by an OtpUser (not an ApiUser or AdminUser).
        if (requestingUser.otpUser != null) {
            belongsToUser = userId.equals(requestingUser.otpUser.id);
        }

        if (belongsToUser) {
            return true;
        } else if (requestingUser.apiUser != null) {
            // get the required OTP user to confirm they are associated with the requesting API user.
            OtpUser otpUser = Persistence.otpUsers.getById(userId);
            if (requestingUser.canManageEntity(otpUser)) {
                return true;
            }
        } else if (requestingUser.isAdmin()) {
            // If not managing self, user must have manage permission.
            for (Permission permission : requestingUser.adminUser.permissions) {
                if (permission.canManage(this.getClass())) return true;
            }
        }
        // Fallback to Model#userCanManage.
        return super.canBeManagedBy(requestingUser);
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
        // If for some reason a leading ? is present in queryParams, skip it.
        // (We already include a ? when calling URLEncodedUtils.parse below.)
        String queryParamsWithoutQuestion = queryParams.startsWith("?")
            ? queryParams.substring(1)
            : queryParams;

        return URLEncodedUtils.parse(
            new URI(String.format("http://example.com/plan?%s", queryParamsWithoutQuestion)),
            UTF_8
        ).stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
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

