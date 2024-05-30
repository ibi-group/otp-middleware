package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

/** Associates an agency to the correct bus notification handler. */
public class AgencyAction {

    /** Agency id. */
    public String agencyId;

    /** The fully-qualified Java class to execute. */
    public String trigger;

    public AgencyAction() {
    }

    public AgencyAction(String agencyId, String trigger) {
        this.agencyId = agencyId;
        this.trigger = trigger;
    }
}
