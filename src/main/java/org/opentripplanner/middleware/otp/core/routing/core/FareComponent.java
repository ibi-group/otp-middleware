package org.opentripplanner.middleware.otp.core.routing.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.opentripplanner.middleware.otp.core.model.FeedScopedId;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * FareComponent is a sequence of routes for a particular fare.
 * </p>
 */
public class FareComponent {

    public FeedScopedId fareId;
    public Money price;
    public List<FeedScopedId> routes;

    public FareComponent() {
    }

    public FareComponent(FeedScopedId fareId, Money amount) {
        this.fareId = fareId;
        price = amount;
        routes = new ArrayList<FeedScopedId>();
    }

    public void addRoute(FeedScopedId routeId) {
        routes.add(routeId);
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer("FareComponent(");
        buffer.append(fareId.toString());
        buffer.append(", ");
        buffer.append(price.toString());
        buffer.append(", ");
        for (FeedScopedId routeId : routes) {
            buffer.append(routeId.toString());
            buffer.append(", ");
        }
        buffer.append(")");
        return buffer.toString();
    }
}


