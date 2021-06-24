package org.opentripplanner.middleware.bugsnag;

import com.bugsnag.Bugsnag;
import com.bugsnag.Report;
import com.bugsnag.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * Bugsnag util for reporting errors to the project defined by the Bugsnag project notifier API key.
 *
 * A Bugsnag project identifier key is unique to a Bugsnag project and allows errors to be saved against it. This key
 * can be obtained by logging into Bugsnag (https://app.bugsnag.com), clicking on Projects (left side menu) and
 * selecting the required project. Once selected, the notifier API key is presented.
 */
public class BugsnagReporter {
    private static Bugsnag bugsnag;
    private static final Logger LOG = LoggerFactory.getLogger(BugsnagReporter.class);

    /**
     * Initialize Bugsnag using the project notifier API key when the application is first loaded.
     */
    public static void initializeBugsnagErrorReporting() {
        String apiKey = getConfigPropertyAsText("BUGSNAG_PROJECT_NOTIFIER_API_KEY");
        if (apiKey != null) {
            bugsnag = new Bugsnag(apiKey);
        } else {
            LOG.warn("Bugsnag project notifier API key not available. Bugsnag error reporting disabled.");
        }
    }

    /**
     * If Bugsnag has been configured, report error based on provided information.
     */
    public static boolean reportErrorToBugsnag(String message, Throwable throwable) {
        return reportErrorToBugsnag(message, null, throwable);
    }

    /**
     * If Bugsnag has been configured, report error based on provided information. Note: throwable must be non-null.
     */
    public static boolean reportErrorToBugsnag(String message, Object badEntity, Throwable throwable) {
        // If no throwable provided, create a new UnknownError exception so that Bugsnag will accept the error report.
        if (throwable == null) {
            LOG.warn("No exception provided for this error report (message: {}). New UnknownError used instead.", message);
            throwable = new UnknownError("Exception type is unknown! Please add exception where report method is called.");
        }
        // Log error to otp-middleware logs.
        LOG.error(message, throwable);
        // If bugsnag is disabled, make sure to report full error to otp-middleware logs.
        if (bugsnag == null) {
            LOG.warn("Bugsnag error reporting is disabled. Unable to report to Bugsnag this message: {} for this bad entity: {}",
                message,
                badEntity,
                throwable);
            return false;
        }
        // Finally, construct report and send to bugsnag.
        Report report = bugsnag.buildReport(throwable);
        report.setContext(message);
        report.addToTab("debugging", "bad entity", badEntity != null ? badEntity.toString() : "N/A");
        report.setSeverity(Severity.ERROR);
        return bugsnag.notify(report);
    }
}
