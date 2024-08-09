package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import org.opentripplanner.middleware.triptracker.TravelerPosition;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opentripplanner.middleware.utils.DateTimeUtils.getOtpZoneId;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getAgencyIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getRouteIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getStopIdFromPlace;
import static org.opentripplanner.middleware.utils.ItineraryUtils.getTripIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.removeAgencyPrefix;

/**
 * Class containing the expected notify parameters. These will be converted to JSON and make up the body content of
 * the request.
 * <p>
 * 'To' fields omitted as they are not needed for requests for single transit legs.
 */
public class UsRideGwinnettBusOpNotificationMessage {

    public UsRideGwinnettBusOpNotificationMessage() {
        // Required for JSON deserialization.
    }

    /** This is the date format required by the API. The date/time must be provided in UTC. */
    private static final DateTimeFormatter BUS_OPERATOR_NOTIFIER_API_DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /** This is the time format required by the API. */
    public static final DateTimeFormatter BUS_OPERATOR_NOTIFIER_API_TIME_FORMAT = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(getOtpZoneId());

    private static final Map<String, Integer> MOBILITY_CODES_LOOKUP = createMobilityCodesLookup();

    /**
     * Create a relationship between mobility modes and mobility codes.
     */
    private static Map<String, Integer> createMobilityCodesLookup() {
        HashMap<String, Integer> codes = new HashMap<>();
        codes.put("None", 0);
        codes.put("Device", 1);
        codes.put("MScooter", 2);
        codes.put("WChairE", 3);
        codes.put("WChairM", 4);
        codes.put("Some", 5);
        codes.put("LowVision", 6);
        codes.put("Blind", 7);
        codes.put("Device-LowVision", 8);
        codes.put("MScooter-LowVision", 9);
        codes.put("WChairE-LowVision", 10);
        codes.put("WChairM-LowVision", 11);
        codes.put("Some-LowVision", 12);
        codes.put("Device-Blind", 13);
        codes.put("MScooter-Blind", 14);
        codes.put("WChairE-Blind", 15);
        codes.put("WChairM-Blind", 16);
        codes.put("Some-Blind", 17);
        return codes;
    }

    public String timestamp;
    public String agency_id;
    public String from_route_id;
    public String from_trip_id;
    public String from_stop_id;
    public String to_stop_id;
    public String from_arrival_time;
    public Integer msg_type;
    public List<Integer> mobility_codes;
    public boolean trusted_companion;

    public UsRideGwinnettBusOpNotificationMessage(Instant currentTime, TravelerPosition travelerPosition) {
        var nextLeg = travelerPosition.nextLeg;
        this.timestamp = BUS_OPERATOR_NOTIFIER_API_DATE_FORMAT.format(currentTime.atZone(ZoneOffset.UTC));
        this.agency_id = removeAgencyPrefix(getAgencyIdFromLeg(nextLeg));
        this.from_route_id = removeAgencyPrefix(getRouteIdFromLeg(nextLeg));
        this.from_trip_id = removeAgencyPrefix(getTripIdFromLeg(nextLeg));
        this.from_stop_id = removeAgencyPrefix(getStopIdFromPlace(nextLeg.from));
        this.to_stop_id = removeAgencyPrefix(getStopIdFromPlace(nextLeg.to));
        this.from_arrival_time = BUS_OPERATOR_NOTIFIER_API_TIME_FORMAT.format(
            nextLeg.getScheduledStartTime()
        );
        // 1 = Notify, 0 = Cancel.
        this.msg_type = 1;
        this.mobility_codes = getMobilityCode(travelerPosition.mobilityMode);
        this.trusted_companion = false;
    }

    /**
     * Get the mobility code that matches the mobility mode. The API can accept multiple codes (probably to cover
     * multiple travelers at the same stop), but the OTP middleware currently only provides exactly one.
     */
    static List<Integer> getMobilityCode(String mobilityMode) {
        // Fallback on the "None" mobility profile (code 0) if the given mode is unknown.
        return List.of(MOBILITY_CODES_LOOKUP.getOrDefault(mobilityMode, 0));
    }
}
