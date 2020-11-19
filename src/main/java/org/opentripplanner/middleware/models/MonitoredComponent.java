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

@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoredComponent extends Model {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoredComponent.class);
    public String bugsnagProjectId;
    public String name;

    public static void initializeMonitoredComponentsFromConfig() {
        List<MonitoredComponent> configComponents = JsonUtils.getPOJOFromJSONAsList(
            ConfigUtils.getConfigProperty("MONITORED_COMPONENTS"),
            MonitoredComponent.class
        );
        for (MonitoredComponent configComponent : configComponents) {
            LOG.info("Found config component: {} {}", configComponent.name, configComponent.bugsnagProjectId);
            Bson withMatchingBugsnagId = Filters.eq("bugsnagProjectId", configComponent.bugsnagProjectId);
            MonitoredComponent matchingComponent = Persistence.monitoredComponents.getOneFiltered(withMatchingBugsnagId);
            if (matchingComponent != null) {
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
