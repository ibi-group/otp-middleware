package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.FindIterable;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.auth.Permission;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpGraphQLVariables;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.Place;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.tripmonitor.JourneyState;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import spark.Request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

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
    @JsonIgnore
    public String tripTime;

    /**
     * From and to locations for the stored trip.
     */
    public Place from;
    public Place to;

    /**
     * whether the trip is an arriveBy trip
     */
    @JsonIgnore
    public boolean arriveBy;

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
     * Whether to snooze the active or upcoming trip. This is reset upon advancing to the next possible trip date in the
     * CheckMonitoredTrip job.
     */
    public boolean snoozed = false;

    /**
     * Query params. Query parameters influencing trip.
     */
    // TODO: Remove
    public String queryParams;

    /**
     * GraphQL query parameters for OTP.
     */
    public OtpGraphQLVariables otp2QueryParams = new OtpGraphQLVariables();

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
     * Records the last itinerary existence check results for this trip. This keeps a record of the latest checks on
     * whether an itinerary was possible on a certain day of the week.
     */
    public ItineraryExistence itineraryExistence;

    public JourneyState journeyState = new JourneyState();

    /**
     * Whether to notify the user when the monitoring of this trip starts.
     */
    public boolean notifyAtLeadingInterval = true;

    /**
     * The number of attempts made to obtain a trip's itinerary from OTP which matches this trip.
     */
    public int attemptsToGetMatchingItinerary;

    public MonitoredTrip() {
    }

    /**
     * Used only during testing
     */
    public MonitoredTrip(OtpGraphQLVariables otp2QueryParams, OtpDispatcherResponse otpDispatcherResponse) throws JsonProcessingException {
        TripPlan plan = otpDispatcherResponse.getResponse().plan;
        itinerary = plan.itineraries.get(0);

        // extract trip time from parsed params and itinerary
        initializeFromItineraryAndQueryParams(otp2QueryParams);
    }

    /**
     * Checks that, for each query provided, an itinerary exists.
     * @return a summary of the itinerary existence results for each day of the week
     */
    public boolean checkItineraryExistence(
        boolean replaceItinerary,
        Function<OtpRequest, OtpResponse> otpResponseProvider
    ) {
        // Get queries to execute by date.
        List<OtpRequest> queriesByDate = getItineraryExistenceQueries();
        itineraryExistence = new ItineraryExistence(queriesByDate, itinerary, arriveBy, otpResponseProvider);
        itineraryExistence.checkExistence(this);
        boolean itineraryExists = itineraryExistence.allMonitoredDaysAreValid(this);
        // If itinerary should be replaced, do so if all checked days are valid.
        return replaceItinerary && itineraryExists
            ? this.updateTripWithVerifiedItinerary()
            : itineraryExists;
    }

    /**
     * Shorthand for above method using the default otpResponseProvider.
     */
    public boolean checkItineraryExistence(boolean replaceItinerary) {
        return checkItineraryExistence(replaceItinerary, null);
    }

    /**
     * Replace the itinerary provided with the monitored trip
     * with a non-real-time, verified itinerary from the responses provided.
     */
    private boolean updateTripWithVerifiedItinerary() {
        String queryDate = otp2QueryParams.date;
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
            this.initializeFromItineraryAndQueryParams(otp2QueryParams);
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
    public List<OtpRequest> getItineraryExistenceQueries() {
        return ItineraryUtils.getOtpRequestsForDates(
            otp2QueryParams,
            ItineraryUtils.getDatesToCheckItineraryExistence(this)
        );
    }

    /**
     * Initializes a MonitoredTrip by deriving some fields from the currently set itinerary. Also, the realtime info of
     * the itinerary is removed.
     */
    public void initializeFromItineraryAndQueryParams(Request req) throws IllegalArgumentException, JsonProcessingException {
        initializeFromItineraryAndQueryParams(OtpGraphQLVariables.fromMonitoredTripRequest(req));
    }

    /**
     * Initializes a MonitoredTrip by deriving some fields from the currently set itinerary. Also, the realtime info of
     * the itinerary is removed.
     */
    public void initializeFromItineraryAndQueryParams(OtpGraphQLVariables graphQLVariables) throws IllegalArgumentException {
        int lastLegIndex = itinerary.legs.size() - 1;
        from = itinerary.legs.get(0).from;
        to = itinerary.legs.get(lastLegIndex).to;
        this.otp2QueryParams = graphQLVariables;
        this.arriveBy = graphQLVariables.arriveBy;

        // Ensure the itinerary we store does not contain any realtime info.
        clearRealtimeInfo();

        // set the trip time by parsing the query params
        tripTime = graphQLVariables.time;
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

    /**
     * @return true if this trip is one-time, false otherwise.
     */
    @JsonIgnore
    @BsonIgnore
    public boolean isOneTime() {
        return !monday && !tuesday && !wednesday && !thursday && !friday && !saturday && !sunday;
    }
}
