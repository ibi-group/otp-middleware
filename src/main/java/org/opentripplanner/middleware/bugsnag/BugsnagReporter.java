package org.opentripplanner.middleware.bugsnag;

import com.bugsnag.Bugsnag;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

/**
 * Bugsnag util for reporting errors to the project defined by the Bugsnag project notifier API key.
 *
 * A Bugsnag project identifier key is unique to a Bugsnag project and allows errors to be saved against it. This key
 * can be obtained by logging into Bugsnag (https://app.bugsnag.com), clicking on Projects (left side menu) and
 * selecting the required project. Once selected, the notifier API key is presented.
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
     * Provide Bugsnag hook, if available, for reporting errors
     */
    public static Bugsnag get() {
        if (bugsnag == null) {
            initializeBugsnag();
        }
        return bugsnag;
    }


}
