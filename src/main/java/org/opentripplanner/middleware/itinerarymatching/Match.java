package org.opentripplanner.middleware.itinerarymatching;

import java.util.function.BooleanSupplier;

/**
 * Helper class to hold a predicate and a descriptive text if the predicate fails.
 */
public class Match {

    public final BooleanSupplier criterion;
    public final String description;

    public Match(BooleanSupplier criterion, String description) {
        this.criterion = criterion;
        this.description = description;
    }
}
