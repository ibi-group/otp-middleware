package org.opentripplanner.middleware.itinerarymatching;

import java.util.Collection;
import java.util.function.BooleanSupplier;

/**
 * Helper class to hold a match criterion and a descriptive text if the criterion is not met.
 */
public class Match {

    public final BooleanSupplier criterion;
    public final String description;

    public Match(BooleanSupplier criterion, String description) {
        this.criterion = criterion;
        this.description = description;
    }

    /**
     * Evaluate the specified matches as an AND operator.
     * @return a matcher result with the first failing match, if any.
     */
    public static MatcherResult all(Collection<Match> criteria) {
        for (Match m : criteria) {
            if (!m.criterion.getAsBoolean()) {
                return new MatcherResult(m);
            }
        }
        return new MatcherResult(null);
    }
}
