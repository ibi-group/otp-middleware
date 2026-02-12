package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.opentripplanner.middleware.itinerarymatching.ItineraryCheckStatus;
import org.opentripplanner.middleware.itinerarymatching.ItineraryChecker;
import org.opentripplanner.middleware.itinerarymatching.ItineraryMatcher;
import org.opentripplanner.middleware.otp.LegFinder;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.I18nUtils;
import org.opentripplanner.middleware.utils.ItineraryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.opentripplanner.middleware.i18n.Message.ENUM_SEPARATOR;
import static org.opentripplanner.middleware.i18n.Message.TRIP_NOT_POSSIBLE_CHECK;
import static org.opentripplanner.middleware.i18n.Message.TRIP_NOT_POSSIBLE_CHECK_ON_DAY;
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
    @JsonIgnore
    private transient Itinerary referenceItinerary;

    /**
     * Whether the original trip request time is a departure or arrive by time.
     */
    @JsonIgnore
    private transient boolean tripIsArriveBy;

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
     * When the itinerary existence check was run/completed.
     * FIXME: If a monitored trip has not been fully enabled for monitoring, we may want to check the timestamp to
     *  verify that the existence check has not gone stale.
     */
    public Date timestamp = new Date();

    private final transient Function<LocalDate, LegFinder> getLegFinder;
    private transient Function<OtpRequest, OtpResponse> otpResponseProvider = getOtpResponseProvider();
    public static Function<OtpRequest, OtpResponse> otpResponseProviderOverride = null;

    public static LegFinder legFinderOverride = null;

    public ItineraryExistence() {
        this(ignored -> new LegFinder());
    }

    public ItineraryExistence(Function<LocalDate, LegFinder> getLegFinder) {
        this.getLegFinder = getLegFinder;
    }

    public ItineraryExistence(
        List<OtpRequest> otpRequests,
        Function<LocalDate, LegFinder> getLegFinder,
        Itinerary referenceItinerary,
        boolean tripIsArriveBy,
        Function<OtpRequest, OtpResponse> otpResponseProvider
    ) {
        this(getLegFinder);
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
    public String getInvalidDaysOfWeekMessage(Locale locale) {
        List<String> invalidDaysOfWeek = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale);
        String enumSeparator = ENUM_SEPARATOR.get(locale);

        for (DayOfWeek dow : DayOfWeek.values()) {
            ItineraryExistenceResult resultForDayOfWeek = getResultForDayOfWeek(dow);
            if (resultForDayOfWeek != null && !resultForDayOfWeek.isValid()) {
                invalidDaysOfWeek.add(String.format(TRIP_NOT_POSSIBLE_CHECK_ON_DAY.get(locale),
                    dow.getDisplayName(TextStyle.FULL, locale),
                    resultForDayOfWeek.invalidDates.stream()
                        .map(d -> dateFormatter.format(DateTimeFormatter.ISO_LOCAL_DATE.parse(d)))
                        .collect(Collectors.joining(enumSeparator))
                ));
            }
        }
        return String.join(enumSeparator, invalidDaysOfWeek);
    }

    /**
     * Checks whether the itinerary of a trip matches any of the OTP itineraries from the trip query params.
     */
    public void checkExistence(MonitoredTrip trip) {
        long startTime = System.currentTimeMillis();
        boolean useThreading = ItineraryUtils.isOtpRequestThreadingEnabled();

        Map<DayOfWeek, ItineraryCheckStatus> legResponses = useThreading
            ? getItineraryStatusesThreaded(trip.itinerary, otpRequests)
            : getItineraryStatusesNonThreaded(trip.itinerary, otpRequests);

        // Check existence of itinerary in the response for each searched day.
        for (OtpRequest otpRequest : otpRequests) {
            DayOfWeek dayOfWeek = otpRequest.dateTime.getDayOfWeek();
            // Get existing result for day of week if a date for that day of week has already been processed, or create
            // a new one.
            ItineraryExistenceResult result = getResultForDayOfWeek(dayOfWeek);
            if (result == null) {
                result = new ItineraryExistenceResult();
                setResultForDayOfWeek(result, dayOfWeek);
            }

            ItineraryCheckStatus checkerStatus = legResponses.get(otpRequest.dateTime.toLocalDate().getDayOfWeek());
            Itinerary matchingItineraryForDay = checkerStatus.isFailed()
                ? checkOtpResponse(otpResponseProvider, otpRequest, trip.id, referenceItinerary, tripIsArriveBy)
                : checkerStatus.rebuiltItinerary;
            if (matchingItineraryForDay == null) {
                LOG.warn(
                    "Itinerary existence check failed on {} for trip {} - {}",
                    dayOfWeek,
                    trip.id,
                    checkerStatus.getFailedReason()
                );
                // If no match was found for the date, mark day of week as non-existent for the itinerary.
                result.handleInvalidDate(otpRequest.dateTime);
            } else {
                // If a matching itinerary on the same service day as the request date is found,
                // save the date with the matching itinerary.
                // (The matching itinerary will replace the original trip.itinerary.)
                result.handleValidDate(otpRequest.dateTime, matchingItineraryForDay);
            }
        }
        if (!allMonitoredDaysAreValid(trip)) {
            OtpUser user = Persistence.otpUsers.getById(trip.userId);
            Locale locale = I18nUtils.getOtpUserLocale(user);
            this.message = String.format(
                TRIP_NOT_POSSIBLE_CHECK.get(locale),
                getInvalidDaysOfWeekMessage(locale)
            );
            this.error = true;
        }

        long timeToComplete = System.currentTimeMillis() - startTime;
        LOG.info(
            "Time to complete itinerary existence checks: {} ms (Threaded: {})",
            timeToComplete,
            useThreading
        );
    }

    public static Itinerary checkOtpResponse(
        Object otpResponseProvider,
        String tripId,
        Itinerary referenceItinerary
    ) {
        return checkOtpResponse(otpResponseProvider, null, tripId, referenceItinerary, false);
    }

    /**
     * Checks whether there is a matching itinerary in the OTP response for the given request. This is used in cases
     * where the leg check fails.
     */
    public static Itinerary checkOtpResponse(
        Object otpResponseProvider,
        OtpRequest otpRequest,
        String tripId,
        Itinerary referenceItinerary,
        boolean tripIsArriveBy
    ) {
        OtpResponse response;
        if (otpResponseProvider == null) {
            return null;
        }
        if (otpResponseProvider instanceof Function) {
            response = ((Function<OtpRequest, OtpResponse>) otpResponseProvider).apply(otpRequest);
        } else if (otpResponseProvider instanceof Supplier) {
            response = ((Supplier<OtpResponse>) otpResponseProvider).get();
        } else {
            throw new IllegalArgumentException("Unsupported otpResponseProvider type.");
        }

        if (response == null || response.plan == null || response.plan.itineraries == null) {
            LOG.warn("Itinerary existence check failed for trip {} - OTP response was null.", tripId);
            return null;
        }
        return hasMatchingItinerary(
            response,
            referenceItinerary,
            otpRequest != null ? otpRequest.dateTime : null,
            tripIsArriveBy,
            tripId
        );
    }

    /**
     * Checks the OTP response for a match against the reference itinerary.
     */
    private static Itinerary hasMatchingItinerary(
        OtpResponse response,
        Itinerary referenceItinerary,
        ZonedDateTime dateTime,
        boolean tripIsArriveBy,
        String tripId
    ) {
        for (Itinerary candidateItinerary : response.plan.itineraries) {
            if (
                (dateTime == null || ItineraryUtils.occursOnSameServiceDay(candidateItinerary, dateTime, tripIsArriveBy))
                && new ItineraryMatcher(referenceItinerary, candidateItinerary).match()
            ) {
                return candidateItinerary;
            }
        }
        LOG.warn("Itinerary existence check failed for trip {} - No matching itinerary found.", tripId);
        return null;
    }

    private Map<DayOfWeek, ItineraryCheckStatus> getItineraryStatusesNonThreaded(Itinerary itinerary, List<OtpRequest> otpRequestsToProcess) {
        return otpRequestsToProcess
            .stream()
            .collect(Collectors.toMap(
                otpRequest -> otpRequest.dateTime.getDayOfWeek(),
                otpRequest -> getItineraryStatus(otpRequest, itinerary, getLegFinder)
            ));
    }

    /**
     * Execute OTP requests and process the responses in a custom executor. Each response is assign to a day of the week.
     */
    private Map<DayOfWeek, ItineraryCheckStatus> getItineraryStatusesThreaded(Itinerary itinerary, List<OtpRequest> otpRequestsToProcess) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Map<DayOfWeek, ItineraryCheckStatus> itineraryCheckStatuses = ItineraryUtils.collectResponses(
            otpRequestsToProcess
                .stream()
                .collect(Collectors.toConcurrentMap(
                    otpRequest -> otpRequest.dateTime.getDayOfWeek(),
                    otpRequest -> CompletableFuture.supplyAsync(
                        () -> getItineraryStatus(otpRequest, itinerary, getLegFinder),
                        executor
                    )
                )),
            new EnumMap<>(DayOfWeek.class),
            LOG,
            "itinerary check status"
        );
        OtpDispatcher.waitForTimeoutThenCancelPendingRequests(executor);
        return itineraryCheckStatuses;
    }

    private static ItineraryCheckStatus getItineraryStatus(OtpRequest request, Itinerary itinerary, Function<LocalDate, LegFinder> getLegFinder) {
        LocalDate requestDate = request.dateTime.toLocalDate();
        ItineraryChecker checker = new ItineraryChecker(
            itinerary,
            OtpMiddlewareMain.inTestEnvironment && legFinderOverride != null
                ? legFinderOverride
                : getLegFinder.apply(requestDate),
            requestDate
        );
        return checker.checkLegs();
    }

    private static OtpResponse getOtpResponse(OtpRequest otpRequest) {
        return OtpDispatcher.sendOtpRequestWithErrorHandling(otpRequest.requestParameters);
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
