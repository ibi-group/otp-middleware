package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;

/**
 * This class holds an {@link ItineraryExistenceResult} for each day of the week,
 * so that clients can determine whether a trip can be regularly monitored on that
 * particular day of the week.
 */
public class ItineraryExistence extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryExistence.class);

    /**
     * Initial set of requests on which to base the itinerary existence checks. We do not want these persisted.
     */
    private transient List<OtpRequest> otpRequests;
    /**
     * The initial reference itinerary to compare against itinerary match candidates.
     */
    private transient Itinerary referenceItinerary;
    public ItineraryExistenceResult monday;
    public ItineraryExistenceResult tuesday;
    public ItineraryExistenceResult wednesday;
    public ItineraryExistenceResult thursday;
    public ItineraryExistenceResult friday;
    public ItineraryExistenceResult saturday;
    public ItineraryExistenceResult sunday;
    /**
     * Message regarding the result of the itinerary existence check.
     */
    public String message;
    /**
     * If an error was encountered during itinerary checks, or if the check determined that not all checked days were
     * valid. FIXME: this should be an enum most likely.
     */
    public boolean error;

    /**
     * Whether the original trip request time is a departure or arrive by time.
     */
    private transient boolean tripIsArriveBy;

    /**
     * When the itinerary existence check was run/completed.
     * FIXME: If a monitored trip has not been fully enabled for monitoring, we may want to check the timestamp to
     *  verify that the existence check has not gone stale.
     */
    public Date timestamp = new Date();

    private transient Function<OtpRequest, OtpResponse> otpResponseProvider = getOtpResponseProvider();

    public static Function<OtpRequest, OtpResponse> otpResponseProviderOverride = null;

    // Required for persistence.
    public ItineraryExistence() {}

    public ItineraryExistence(
        List<OtpRequest> otpRequests,
        Itinerary referenceItinerary,
        boolean tripIsArriveBy,
        Function<OtpRequest, OtpResponse> otpResponseProvider
    ) {
        this.otpRequests = otpRequests;
        this.referenceItinerary = referenceItinerary;
        this.tripIsArriveBy = tripIsArriveBy;
        if (otpResponseProvider != null) this.otpResponseProvider = otpResponseProvider;
    }

    private Function<OtpRequest, OtpResponse> getOtpResponseProvider() {
        return OtpMiddlewareMain.inTestEnvironment && otpResponseProviderOverride != null
            ? otpResponseProviderOverride
            : ItineraryExistence::getOtpResponse;
    }

    /**
     * Helper function to extract the existence check for a particular day of the week.
     */
    public ItineraryExistenceResult getResultForDayOfWeek(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return monday;
            case TUESDAY: return tuesday;
            case WEDNESDAY: return wednesday;
            case THURSDAY: return thursday;
            case FRIDAY: return friday;
            case SATURDAY: return saturday;
            case SUNDAY: return sunday;
        }
        throw new IllegalArgumentException("Invalid day of week provided!");
    }

    /**
     * Helper function to set the existence check for a particular day of the week.
     */
    public void setResultForDayOfWeek(ItineraryExistenceResult result, DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                monday = result;
                break;
            case TUESDAY:
                tuesday = result;
                break;
            case WEDNESDAY:
                wednesday = result;
                break;
            case THURSDAY:
                thursday = result;
                break;
            case FRIDAY:
                friday = result;
                break;
            case SATURDAY:
                saturday = result;
                break;
            case SUNDAY:
                sunday = result;
                break;
            default:
                break;
        }
    }

    /**
     * @return true if all monitored days of the week for a trip are valid.
     */
    public boolean allMonitoredDaysAreValid(MonitoredTrip trip) {
        return (!trip.monday || itineraryExistsOn(monday)) &&
            (!trip.tuesday || itineraryExistsOn(tuesday)) &&
            (!trip.wednesday || itineraryExistsOn(wednesday)) &&
            (!trip.thursday || itineraryExistsOn(thursday)) &&
            (!trip.friday || itineraryExistsOn(friday)) &&
            (!trip.saturday || itineraryExistsOn(saturday)) &&
            (!trip.sunday || itineraryExistsOn(sunday));
    }

    /**
     * @return The first {@link Itinerary} found for the given {@link DayOfWeek}.
     */
    public Itinerary getItineraryForDayOfWeek(DayOfWeek dow) {
        ItineraryExistenceResult resultForDay = getResultForDayOfWeek(dow);
        return itineraryExistsOn(resultForDay) && !resultForDay.itineraries.isEmpty()
            ? resultForDay.itineraries.get(0)
            : null;
    }

    /**
     * @return A string containing the days of week (and first date found) for which the trip is not possible.
     */
    @JsonIgnore
    @BsonIgnore
    public String getInvalidDaysOfWeekMessage() {
        List<String> invalidDaysOfWeek = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            ItineraryExistenceResult resultForDayOfWeek = getResultForDayOfWeek(dow);
            if (resultForDayOfWeek != null && !resultForDayOfWeek.isValid()) {
                invalidDaysOfWeek.add(String.format("%s (no trip %s)",
                    dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH), // TODO: i18n
                    String.join(", ", resultForDayOfWeek.invalidDates)
                ));
            }
        }
        return String.join(", ", invalidDaysOfWeek);
    }

    /**
     * Checks whether the itinerary of a trip matches any of the OTP itineraries from the trip query params.
     */
    public void checkExistence(MonitoredTrip trip) {
        // TODO: Consider multi-threading?
        // Check existence of itinerary in the response for each OTP request.
        int index = 0;
        for (OtpRequest otpRequest : otpRequests) {
            index++;
            boolean hasMatchingItinerary = false;
            DayOfWeek dayOfWeek = otpRequest.dateTime.getDayOfWeek();
            // Get existing result for day of week if a date for that day of week has already been processed, or create
            // a new one.
            ItineraryExistenceResult result = getResultForDayOfWeek(dayOfWeek);
            if (result == null) {
                result = new ItineraryExistenceResult();
                setResultForDayOfWeek(result, dayOfWeek);
            }

            // Send off each plan query to OTP.
            OtpResponse response = this.otpResponseProvider.apply(otpRequest);
            if (response == null) {
                LOG.warn("Itinerary existence check failed on {} for trip {} - OTP response was null.", dayOfWeek , trip.id);
            } else {
                TripPlan plan = response.plan;

                // Handle response if valid itineraries exist.
                if (plan != null && plan.itineraries != null) {
                    for (Itinerary itineraryCandidate : plan.itineraries) {
                        // If a matching itinerary on the same service day as the request date is found,
                        // save the date with the matching itinerary.
                        // (The matching itinerary will replace the original trip.itinerary.)
                        if (
                            ItineraryUtils.occursOnSameServiceDay(itineraryCandidate, otpRequest.dateTime, tripIsArriveBy) &&
                                ItineraryUtils.itinerariesMatch(referenceItinerary, itineraryCandidate)
                        ) {
                            result.handleValidDate(otpRequest.dateTime, itineraryCandidate);
                            hasMatchingItinerary = true;
                        }
                    }
                }

                if (!hasMatchingItinerary) {
                    // If no match was found for the date, mark day of week as non-existent for the itinerary.
                    result.handleInvalidDate(otpRequest.dateTime);

                    // Log if the itinerary didn't exist "today"
                    if (index == 1) {
                        LOG.warn("Itinerary existence check failed 'today' for trip {} - params: {}", trip.id, JsonUtils.toJson(trip.otp2QueryParams));
                        LOG.warn("Itinerary existence check failed 'today' for trip {} - saved itinerary: {}", trip.id, JsonUtils.toJson(trip.itinerary));
                        LOG.warn("Itinerary existence check failed 'today' for trip {} - OTP itineraries: {}", trip.id, JsonUtils.toJson(plan.itineraries));
                    }
                }
            }
        }
        if (!allMonitoredDaysAreValid(trip)) {
            this.message = String.format(
                "The trip is not possible on the following days of the week you have selected: %s. Real-time conditions might have changed since you last planned this trip, so try returning to the trip planner, perform the itinerary search again, and save the result.",
                getInvalidDaysOfWeekMessage()
            );
            this.error = true;
        }
    }

    private static OtpResponse getOtpResponse(OtpRequest otpRequest) {
        return OtpDispatcher.sendOtpRequestWithErrorHandling(otpRequest);
    }

    /**
     * Checks whether there is at least one day of the week where the trip is still possible. If there is, then true is
     * returned.
     */
    public boolean isPossibleOnAtLeastOneMonitoredDayOfTheWeek(MonitoredTrip trip) {
        return (trip.monday && itineraryExistsOn(monday)) ||
            (trip.tuesday && itineraryExistsOn(tuesday)) ||
            (trip.wednesday && itineraryExistsOn(wednesday)) ||
            (trip.thursday && itineraryExistsOn(thursday)) ||
            (trip.friday && itineraryExistsOn(friday)) ||
            (trip.saturday && itineraryExistsOn(saturday)) ||
            (trip.sunday && itineraryExistsOn(sunday));
    }

    public static boolean itineraryExistsOn(ItineraryExistenceResult dayResult) {
        return dayResult != null && dayResult.isValid();
    }

    /**
     * Holds results for an itinerary existence check for a particular day of the week.
     */
    public static class ItineraryExistenceResult {
        /**
         * True if and only if an itinerary is available for all dates tested for existence.
         */
        @JsonProperty
        public boolean isValid() {
            return invalidDates.isEmpty();
        }

        /**
         * Dummy setter required to prevent a deserialization "unknown field" error for itinerary
         * existence requests that include an `itineraryExistence.valid` entry in the body.
         */
        @JsonIgnore
        public void setValid (boolean value) {}

        /**
         * Holds a list of invalid dates an itinerary is not available for the associated day of the week.
         */
        public List<String> invalidDates = new ArrayList<>();

        /**
         * Holds a list of valid dates for which an itinerary exists.
         */
        public List<String> validDates = new ArrayList<>();

        /**
         * Holds any matching itineraries (sorted by date) for the applicable day of the week.
         */
        public transient List<Itinerary> itineraries = new ArrayList<>();

        /**
         * Marks an itinerary as not available for the specified date for the applicable day of the week.
         */
        public void handleInvalidDate(ZonedDateTime date) {
            String dateString = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
            invalidDates.add(dateString);
        }

        /**
         * Adds date to list of valid dates and itinerary to list of itineraries.
         */
        public void handleValidDate(ZonedDateTime date, Itinerary itineraryCandidate) {
            String dateString = DateTimeUtils.getStringFromDate(date.toLocalDate(), DEFAULT_DATE_FORMAT_PATTERN);
            validDates.add(dateString);
            itineraries.add(itineraryCandidate);
        }
    }
}
