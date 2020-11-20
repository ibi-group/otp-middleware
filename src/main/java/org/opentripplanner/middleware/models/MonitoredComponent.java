package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Maps;
import com.mongodb.client.model.Filters;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * This class represents the system components (e.g., servers or UIs) that otp-middleware is expected to monitor. It
 * should serve as a place to collect properties needed for integrations with various monitoring services (e.g.,
 * Bugsnag).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoredComponent extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoredComponent.class);
    /**
     * The field that maps this component to a project in Bugsnag (this is a string UUID).
     *
     * See https://api.bugsnag.com/organizations/<organization_id>/projects for a list of projects.
     */
    public String bugsnagProjectId;
    /**
     * The name of the component (e.g., datatools-server) to display to otp-admin-ui users.
     */
    public String name;

    /**
     * Read in the list of {@link MonitoredComponent} from the MONITORED_COMPONENTS field in the env.yml file.
     */
    public static void initializeMonitoredComponentsFromConfig() {
        List<MonitoredComponent> configComponents;
        try {
            configComponents = JsonUtils.getPOJOFromJSONAsList(
                ConfigUtils.getConfigPropertyAsText("MONITORED_COMPONENTS"),
                MonitoredComponent.class
            );
            if (configComponents == null) {
                throw new IllegalArgumentException("MONITORED_COMPONENTS should not be missing.");
            }
        } catch (Exception e) {
            LOG.error("Could not parse MONITORED_COMPONENTS from config.");
            return;
        }
        for (MonitoredComponent configComponent : configComponents) {
            LOG.info("Found config component: {} {}", configComponent.name, configComponent.bugsnagProjectId);
            Bson withMatchingBugsnagId = Filters.eq("bugsnagProjectId", configComponent.bugsnagProjectId);
            MonitoredComponent matchingComponent = Persistence.monitoredComponents.getOneFiltered(withMatchingBugsnagId);
            if (matchingComponent != null) {
                // TODO: Should we instead replace the component if a new property is detected?
                LOG.info("Skipping import of {} (bugsnagProjectId already exists in collection).", configComponent.name);
            } else {
                LOG.info("Importing {} as new monitored component.", configComponent.name);
                Persistence.monitoredComponents.create(configComponent);
            }
        }
    }

    /**
     * Get {@link MonitoredComponent} by Bugsnag project id to avoid multiple queries to Mongo for the same project.
     */
    @JsonIgnore
    @BsonIgnore
    public static Map<String, MonitoredComponent> getComponentsByProjectId() {
        return Maps.uniqueIndex(
            Persistence.monitoredComponents.getAll(),
            c -> c.bugsnagProjectId
        );
    }

    /**
     * Only admin users can create new components for monitoring.
     */
    @Override
    public boolean canBeCreatedBy(RequestingUser user) {
        return user.isAdmin();
    }
}
