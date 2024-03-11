package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpVersion;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.TripPlan;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

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
     * FIXME: Something other than deprecated Date() class here!
     */
    public Date timestamp = new Date();

    // Required for persistence.
    public ItineraryExistence() {}

    public ItineraryExistence(List<OtpRequest> otpRequests, Itinerary referenceItinerary, boolean tripIsArriveBy) {
        this.otpRequests = otpRequests;
        this.referenceItinerary = referenceItinerary;
        this.tripIsArriveBy = tripIsArriveBy;
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
     * Helper method to adapt OTP request parameters to GraphQL's JSON {@code variables} object, limited to
     * variables specified in the GraphQL query plan.  Parameter values are all simply {@code String}s, so we
     * can't use JSON packages to get the types right, must hardcode them specifically for ItineraryExistence
     * so that only the values that are actually {@code String}s have {@code \"} around them.
     */
    private static String paramsToVariables(Map<String, String> params) {
        StringBuilder builder = new StringBuilder("{");
        params.forEach((k, v) -> {
            switch (k) {
                // Don't put quotes around these values.
                case "arriveBy":
                case "numItineraries":
                    builder.append("\"" + k + "\":" + v + ",");
                    break;

                // Put quotes around those values, they are String types.
                case "date":
                case "time":
                case "fromPlace":
                case "toPlace":
                    builder.append("\"" + k + "\":\"" + v + "\",");
                    break;

                // From "mode" to GraphQL "$modes".
                case "mode":
                    builder.append("\"modes\":[");
                    Stream.of(v.split(",")).forEach(m -> builder.append("{\"mode\":\"" + m + "\"},"));
                    builder.deleteCharAt(builder.length() - 1); // Remove trailing comma.
                    builder.append("],");
                    break;

                default:
                    break;
            }
        });
        builder.setCharAt(builder.length() - 1, '}'); // Replace trailing comma with closing brace.
        return builder.toString();
    }

    /**
     * Helper function to set the existence check for a particular day of the week.
     */
    private void setResultForDayOfWeek(ItineraryExistenceResult result, DayOfWeek dayOfWeek) {
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
     * Checks whether all checked days of the week are valid.
     * @return true if all days are either valid (i.e., the day was checked) or null (i.e., the day was not checked).
     */
    public boolean allCheckedDaysAreValid() {
        return (monday == null || monday.isValid()) &&
            (tuesday == null || tuesday.isValid()) &&
            (wednesday == null || wednesday.isValid()) &&
            (thursday == null || thursday.isValid()) &&
            (friday == null || friday.isValid()) &&
            (saturday == null || saturday.isValid()) &&
            (sunday == null || sunday.isValid());
    }

    /**
     * @return The first {@link Itinerary} found for the given {@link DayOfWeek}.
     */
    public Itinerary getItineraryForDayOfWeek(DayOfWeek dow) {
        ItineraryExistenceResult resultForDay = getResultForDayOfWeek(dow);
        return resultForDay != null && resultForDay.isValid() && resultForDay.itineraries.size() > 0
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
    public void checkExistence() {
        // TODO: Consider multi-threading?
        // Check existence of itinerary in the response for each OTP request.
        for (OtpRequest otpRequest : otpRequests) {
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
            String variables = ItineraryExistence.paramsToVariables(otpRequest.requestParameters);
            OtpDispatcherResponse response = OtpDispatcher.sendGraphQLPostRequest(OtpVersion.OTP2, variables);
            TripPlan plan = null;
            try {
                plan = response.getResponse().plan;
            } catch (JsonProcessingException e) {
                LOG.error("Could not parse plan response for otpRequest {}", otpRequest, e);
            }
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
            }
        }
        if (!this.allCheckedDaysAreValid()) {
            this.message = String.format(
                "The trip is not possible on the following days of the week you have selected: %s",
                getInvalidDaysOfWeekMessage()
            );
            this.error = true;
        }
    }

    /**
     * Checks whether there is at least one day of the week where the trip is still possible. If there is, then true is
     * returned.
     */
    public boolean isPossibleOnAtLeastOneMonitoredDayOfTheWeek(MonitoredTrip trip) {
        return (trip.monday && monday != null && monday.isValid()) ||
            (trip.tuesday && tuesday != null && tuesday.isValid()) ||
            (trip.wednesday && wednesday != null && wednesday.isValid()) ||
            (trip.thursday && thursday != null && thursday.isValid()) ||
            (trip.friday && friday != null && friday.isValid()) ||
            (trip.saturday && saturday != null && saturday.isValid()) ||
            (trip.sunday && sunday != null && sunday.isValid());
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
            return invalidDates.size() == 0;
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
