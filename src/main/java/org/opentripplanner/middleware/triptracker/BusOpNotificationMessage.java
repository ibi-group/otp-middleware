package org.opentripplanner.middleware.triptracker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class containing the expected notify parameters. These will be converted to JSON and make up the body content of
 * the request.
 * <p>
 * 'To' fields omitted as they are not needed for requests for single transit legs.
 */
public class BusOpNotificationMessage {

    public BusOpNotificationMessage() {
        // Required for JSON deserialization.
    }

    private static final DateTimeFormatter BUS_OPERATOR_NOTIFIER_API_DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneId.systemDefault());

    public static final DateTimeFormatter BUS_OPERATOR_NOTIFIER_API_TIME_FORMAT = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private static final Map<String, Integer> MOBILITY_CODES_LOOKUP = createMobilityCodesLookup();

    /**
     * Create a relationship between mobility modes and mobility codes.
     */
    private static Map<String, Integer> createMobilityCodesLookup() {
        HashMap<String, Integer> codes = new HashMap<>();
        codes.put("Device", 1);
        codes.put("Mscooter", 2);
        codes.put("WchairE", 3);
        codes.put("WchairM", 4);
        codes.put("Some", 5);
        codes.put("LowVision", 6);
        codes.put("Blind", 7);
        codes.put("Device-LowVision", 8);
        codes.put("Mscooter-LowVision", 9);
        codes.put("WChairE-LowVision", 10);
        codes.put("WChairM-LowVision", 11);
        codes.put("Some-LowVision", 12);
        codes.put("Device-Blind", 13);
        codes.put("Mscooter-Blind", 14);
        codes.put("WchairE-Blind", 15);
        codes.put("WchairM-Blind", 16);
        codes.put("Some-Blind", 17);
        return codes;
    }

    public String timestamp;
    public String agency_id;
    public String from_route_id;
    public String from_trip_id;
    public String from_stop_id;
    public String from_arrival_time;
    public Integer msg_type;
    public List<Integer> mobility_codes;
    public boolean trusted_companion;

    public BusOpNotificationMessage(Instant timestamp, TravelerPosition travelerPosition) {
        this.timestamp = BUS_OPERATOR_NOTIFIER_API_DATE_FORMAT.format(timestamp);
        this.agency_id = removeAgencyPrefix(travelerPosition.nextLeg.agency.id);
        this.from_route_id = removeAgencyPrefix(travelerPosition.nextLeg.route.id);
        this.from_trip_id = travelerPosition.nextLeg.trip.id;
        this.from_stop_id = travelerPosition.nextLeg.from.stop.id;
        this.from_arrival_time = BUS_OPERATOR_NOTIFIER_API_TIME_FORMAT.format(
            travelerPosition.nextLeg.getScheduledStartTime().toInstant()
        );
        // 1 = Notify, 0 = Cancel.
        this.msg_type = 1;
        this.mobility_codes = getMobilityCode(travelerPosition.mobilityMode);
        this.trusted_companion = false;
    }

    /**
     * Get the second element from value by removing the OTP agency prefix.
     * E.g. GwinnettCountyTransit:GCT will return just GCT.
     */
    private String removeAgencyPrefix(String value) {
        return (value != null) ? value.split(":")[1] : null;
    }

    /**
     * Get the mobility code that matches the mobility mode. Although the API can accept multiple codes, OTP middleware
     * currently only provides one.
     */
    private static List<Integer> getMobilityCode(String mobilityMode) {
        List<Integer> mobilityCodes = new ArrayList<>();
        Integer code = MOBILITY_CODES_LOOKUP.get(mobilityMode);
        if (code != null) {
            mobilityCodes.add(code);
        }
        return mobilityCodes;
    }

}
