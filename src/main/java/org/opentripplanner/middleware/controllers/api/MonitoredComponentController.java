package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.models.MonitoredComponent;
import org.opentripplanner.middleware.persistence.Persistence;

/**
 * Implementation of the {@link ApiControllerImpl} for {@link MonitoredComponent}.
 */
public class MonitoredComponentController extends ApiControllerImpl<MonitoredComponent> {
    /**
     * Note: this controller must sit behind the /admin path. This ensures
     * that the requesting user is checked for admin authorization (handled by
     * {@link org.opentripplanner.middleware.auth.Auth0Connection#checkUserIsAdmin}).
     */
    public MonitoredComponentController(String apiPrefix) {
        super(apiPrefix, Persistence.monitoredComponents);
    }
}