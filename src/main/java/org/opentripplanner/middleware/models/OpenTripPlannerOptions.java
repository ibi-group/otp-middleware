package org.opentripplanner.middleware.models;

import java.io.Serializable;

/**
 * Options associated with users of OpenTripPlanner client.
 */
public class OpenTripPlannerOptions implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Only store trip planning requests/results if a user has explicitly opted in. */
    public boolean storeResults;
    // TODO Determine options.
}
