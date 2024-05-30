package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripStatus;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.opentripplanner.middleware.utils.ItineraryUtils.getAgencyIdFromLeg;
import static org.opentripplanner.middleware.utils.ItineraryUtils.removeAgencyPrefix;

/** Holds configured bus notification actions. */
public class BusOperatorActions {
    private static final Logger LOG = LoggerFactory.getLogger(BusOperatorActions.class);

    public static final String BUS_NOTIFIER_ACTIONS_YML = "configurations/default/bus-notifier-actions.yml";

    private static BusOperatorActions defaultInstance;

    private final List<AgencyAction> agencyActions;

    public static BusOperatorActions getDefault() {
        if (defaultInstance == null) {
            try (InputStream stream = new FileInputStream(BUS_NOTIFIER_ACTIONS_YML)) {
                JsonNode busNotifierActionsYml = YamlUtils.yamlMapper.readTree(stream);
                defaultInstance = new BusOperatorActions(JsonUtils.getPOJOFromJSONAsList(busNotifierActionsYml, AgencyAction.class));
            } catch (IOException e) {
                LOG.error("Error parsing trip-actions.yml", e);
                throw new RuntimeException(e);
            }
        }
        return defaultInstance;
    }

    public BusOperatorActions(List<AgencyAction> agencyActions) {
        this.agencyActions = agencyActions;
    }

    /**
     * Get the action that matches the given agency id.
     */
    public AgencyAction getAgencyAction(TravelerPosition travelerPosition) {
        String agencyId = removeAgencyPrefix(getAgencyIdFromLeg(travelerPosition.nextLeg));
        if (agencyId != null) {
            for (AgencyAction agencyAction : agencyActions) {
                if (agencyAction.agencyId.equalsIgnoreCase(agencyId)) {
                    return agencyAction;
                }
            }
        }
        return null;
    }

    /**
     * Get the correct action for agency and send notification.
     */
    public void handleSendNotificationAction(TripStatus tripStatus, TravelerPosition travelerPosition) {
        AgencyAction action = getAgencyAction(travelerPosition);
        if (action != null) {
            BusOperatorInteraction interaction = getBusOperatorInteraction(action);
            try {
                interaction.sendNotification(tripStatus, travelerPosition);
            } catch (Exception e) {
                LOG.error("Could not trigger class {} for agency {}", action.trigger, action.agencyId, e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Get the correct action for agency and cancel notification.
     */
    public void handleCancelNotificationAction(TravelerPosition travelerPosition) {
        AgencyAction action = getAgencyAction(travelerPosition);
        if (action != null) {
            BusOperatorInteraction interaction = getBusOperatorInteraction(action);
            try {
                interaction.cancelNotification(travelerPosition);
            } catch (Exception e) {
                LOG.error("Could not trigger class {} for agency {}", action.trigger, action.agencyId, e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Get the bus operator class for the correct agency.
     */
    private BusOperatorInteraction getBusOperatorInteraction(AgencyAction action) {
        BusOperatorInteraction interaction;
        try {
            Class<?> interactionClass = Class.forName(action.trigger);
            interaction = (BusOperatorInteraction) interactionClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LOG.error("Error instantiating class {}", action.trigger, e);
            throw new RuntimeException(e);
        }
        return interaction;
    }
}
