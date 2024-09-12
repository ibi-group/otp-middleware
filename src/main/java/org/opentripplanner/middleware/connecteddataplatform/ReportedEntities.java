package org.opentripplanner.middleware.connecteddataplatform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton that holds configuration data about entities to report.
 * Note: Fields are PascalCased to reflect the casing of respective class names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportedEntities {
    private static final Logger LOG = LoggerFactory.getLogger(ReportedEntities.class);

    private static ReportedEntities defaultInstance;
    private static boolean initialized;

    public String MonitoredTrip;
    public String OtpUser;
    public String TrackedJourney;
    public String TripRequest;
    public String TripSummary;

    /**
     * Read in the {@link ReportedEntities} from the CONNECTED_DATA_PLATFORM_REPORTED_ENTITIES field in the env.yml file.
     */
    private static ReportedEntities initializeFromConfig() {
        try {
            JsonNode jsonNode = ConfigUtils.getConfigProperty("CONNECTED_DATA_PLATFORM_REPORTED_ENTITIES");
            if (jsonNode == null) return null;
            return JsonUtils.getPOJOFromJSON(
                jsonNode.toString(),
                ReportedEntities.class
            );
        } catch (Exception e) {
            LOG.error("Could not parse CONNECTED_DATA_PLATFORM_REPORTED_ENTITIES from config.", e);
            return null;
        }
    }

    public static ReportedEntities defaults() {
        if (!initialized) {
            defaultInstance = initializeFromConfig();
            initialized = true;
        }
        return defaultInstance;
    }
}
