package org.opentripplanner.middleware.itinerarymatching;

/**
 * Helper class that holds results of a match evaluation.
 */
public class MatcherResult {
    public final Match failingMatch;

    public MatcherResult(Match failingMatch) {
        this.failingMatch = failingMatch;
    }

    public boolean isSuccessful() {
        return failingMatch == null;
    }

    public String getFailingMatchDescription() {
        return failingMatch != null ? failingMatch.descriptionGetter.get() : null;
    }
}
