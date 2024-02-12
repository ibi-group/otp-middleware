package org.opentripplanner.middleware.utils;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.models.TripMonitorAlertNotification;
import org.opentripplanner.middleware.models.TripMonitorNotification;
import org.opentripplanner.middleware.otp.response.LocalizedAlert;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.tripmonitor.jobs.NotificationType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.zenika.snapshotmatcher.SnapshotMatcher.matchesSnapshot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.opentripplanner.middleware.utils.I18nUtils.label;

/**
 * Unit tests for {@code resources/templates/*.ftl} Freemarker Template Language files.
 * <p>
 * When the tests are first run, the {@code com.zenika.snapshotmatcher.SnapshotMatcher} class will create JSON snapshot
 * files in {@code resources/snapshots/org/opentripplanner/middleware} which are used thereafter to compare with the
 * results of the template files.  If you change any {@code *.ftl} files you will need to delete their corresponding
 * snapshot files, run the tests to create new snapshots, and then commit those files along with new template files.
 */
class TemplateUtilsTest extends OtpMiddlewareTestEnvironment {
    /**
     * A parameterized test that checks whether various templates render in a way that matches a snapshot. The name of
     * the test case is guaranteed to be unique due to a check in the {@link TemplateRenderingTestCase} constructor. The
     * test case name is then used to generate uniquely-named snapshots so that individual test cases can be properly be
     * ran.
     */
    @ParameterizedTest
    @MethodSource("createTemplateRenderingTestCases")
    void canRenderTemplates(TemplateRenderingTestCase testCase) throws Exception {
        assertMatchesSnapshot(
            TemplateUtils.renderTemplate(testCase.templatePath, testCase.templateData),
            testCase.testCaseName.replace(" ", "_")
        );
    }

    /**
     * Creates the template rendering test cases. If there are any templates that are used in the main code, they should
     * always be tested here to make sure their snapshot and mock data are able to render what is expected. Intermediate
     * files that templates inherit from don't need to be tested individually.
     */
    static List<TemplateRenderingTestCase> createTemplateRenderingTestCases() {
        List<TemplateRenderingTestCase> testCases = new ArrayList<>();

        // Event Errors (this is a template for an email that is sent in BugsnagEventHandlingJob#sendEmailForEvents)
        Map<String, String> errorEventsData = Map.of("subject", "test subject");
        testCases.add(new TemplateRenderingTestCase(
            errorEventsData, "EventErrorsText.ftl", "Event Errors Text Email"
        ));
        testCases.add(new TemplateRenderingTestCase(
            errorEventsData, "EventErrorsHtml.ftl", "Event Errors Html Email"
        ));

        // Trip Monitor Notifications tests (for CheckMonitoredTrip#sendNotifications).
        Locale locale = Locale.ENGLISH;
        String tripLinkLabel = Message.TRIP_LINK_TEXT.get(locale);
        String tripUrl = "http://otp-ui.example.com/#/account/trips/test-trip-id";
        Map<String, Object> notificationsData = Map.of(
            "tripNameOrReminder", "Test Trip",
            "emailGreeting", Message.TRIP_EMAIL_GREETING.get(locale),
            "tripLinkLabelAndUrl", label(tripLinkLabel, tripUrl, locale),
            "tripLinkAnchorLabel", tripLinkLabel,
            "tripUrl", tripUrl,
            "emailFooter", String.format(Message.TRIP_EMAIL_FOOTER.get(locale), "Test Trip Planner"),
            "manageLinkText", Message.TRIP_EMAIL_MANAGE_NOTIFICATIONS.get(locale),
            "manageLinkUrl", "http://otp-ui.example.com/#/account/settings",
            "notifications", List.of(
                createAlertNotification(),
                new TripMonitorNotification(NotificationType.DEPARTURE_DELAY, "This is the departure delay text")
        ));
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripSms.ftl", "Monitored Trip SMS"
        ));
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripPush.ftl", "Monitored Trip Push"
        ));
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripText.ftl", "Monitored Trip Text Email"
        ));
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripHtml.ftl", "Monitored Trip HTML Email"
        ));
        return testCases;
    }

    private static TripMonitorAlertNotification createAlertNotification() {
        // Create a notification with new and existing alerts,
        // mixing cases of alerts without header and alerts without description.
        Set<LocalizedAlert> newAlerts = Set.of(
            new LocalizedAlert("New Alert 1", null),
            new LocalizedAlert(null, "New Alert 2 description")
        );
        Set<LocalizedAlert> previousAlerts = Set.of(
            new LocalizedAlert("Resolved Alert", null)
        );

        return TripMonitorAlertNotification.createAlertNotification(previousAlerts, newAlerts, Locale.ENGLISH);
    }

    private static class TemplateRenderingTestCase {
        private static final Set<String> testCaseNames = new HashSet<>();
        public final Object templateData;
        public final String templatePath;
        public final String testCaseName;

        private TemplateRenderingTestCase(Object templateData, String templatePath, String testCaseName) {
            // enforce unique test case names so that snapshotting works properly
            if (testCaseNames.contains(testCaseName)) {
                throw new IllegalArgumentException(
                    String.format(
                        "A test case with the name %s already exists! Test case names must be unique.",
                        testCaseName
                    )
                );
            }
            testCaseNames.add(testCaseName);
            this.templateData = templateData;
            this.templatePath = templatePath;
            this.testCaseName = testCaseName;
        }

        @Override public String toString() {
            return testCaseName;
        }
    }

    /**
     * Wrapper method for {@link MatcherAssert#assertThat} that handles replacing Windows system line separators with
     * Unix line separators in the specified actual string. This ensures that tests run on Windows and Linux both use
     * the \n character for line separators and the snapshots match across platforms.
     *
     * TODO: Fix this in https://github.com/conveyal/java-snapshot-matcher
     */
    private static void assertMatchesSnapshot(String actual, String snapshotName) {
        String actualWithStandardLineSeparators = actual.replaceAll("\r\n", "\n");
        assertThat(actualWithStandardLineSeparators, matchesSnapshot(snapshotName));
    }
}