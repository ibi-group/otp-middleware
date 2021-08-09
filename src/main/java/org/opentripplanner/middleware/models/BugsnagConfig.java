package org.opentripplanner.middleware.models;

/**
 * Use to record whether or not the event data has been seeded. This is a one time event which is initiated on start-up.
 */
public class BugsnagConfig extends Model {
    public String configId;
    public boolean seedEventData;

    /** This no-arg constructor exists to make MongoDB happy. */
    public BugsnagConfig() {
    }

    public BugsnagConfig(String configId, boolean seedEventData) {
        this.configId = configId;
        this.seedEventData = seedEventData;
    }
}
