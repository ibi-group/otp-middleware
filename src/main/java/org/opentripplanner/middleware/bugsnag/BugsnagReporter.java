package org.opentripplanner.middleware.bugsnag;

import com.bugsnag.Bugsnag;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Bugsnag util for reporting errors
 */
public class BugsnagReporter {
    private static Bugsnag bugsnag;

    /**
     * Initialize Bugsnag using API key when application is first loaded
     */
    private static void initializeBugsnag() {
        String apiKey = getConfigPropertyAsText("BUGSNAG_PROJECT_NOTIFIER_API_KEY");
        if (apiKey != null) {
            bugsnag = new Bugsnag(apiKey);
        }
    }

    /**
     * Provide bugsnag hook, if available, for reporting errors
     */
    public static Bugsnag get() {
        if (bugsnag == null) {
            initializeBugsnag();
        }
        return bugsnag;
    }


}
