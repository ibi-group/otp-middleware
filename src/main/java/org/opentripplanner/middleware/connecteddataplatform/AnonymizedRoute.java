package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.otp.response.Route;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Route}.
 */
public class AnonymizedRoute {

    public String id;
    public String longName;
    public String shortName;
    public Integer type;

    /**
     * This no-arg constructor exists for JSON deserialization.
     */
    public AnonymizedRoute() {
    }

    public AnonymizedRoute(Route route) {
        this.id = route.id;
        this.longName = route.longName;
        this.shortName = route.shortName;
        this.type = route.type;
    }
}
