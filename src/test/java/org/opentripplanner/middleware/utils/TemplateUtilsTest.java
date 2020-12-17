package org.opentripplanner.middleware.utils;

import com.zenika.snapshotmatcher.SnapshotMatcher;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.zenika.snapshotmatcher.SnapshotMatcher.matchesSnapshot;
import static org.hamcrest.MatcherAssert.assertThat;

public class TemplateUtilsTest extends OtpMiddlewareTest {
    /**
     * A parameterized test that checks whether various templates render in a way that matches a snapshot. The name of
     * the test case is guaranteed to be unique due to a check in the {@link TemplateRenderingTestCase} constructor. The
     * test case name is then used to generate uniquely-named snapshots so that individual test cases can be properly be
     * ran.
     */
    @ParameterizedTest
    @MethodSource("createTemplateRenderingTestCases")
    public void canRenderTemplates(TemplateRenderingTestCase testCase) throws Exception {
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
    public static List<TemplateRenderingTestCase> createTemplateRenderingTestCases() {
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
        Map<String, Object> notificationsData = Map.of(
            "tripId", "18f642d5-f7a8-475a-9469-800129e6c0b3",
            "notifications", List.of("Test notification.", "Another notification.")
        );
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripSms.ftl", "Monitored Trip SMS"
        ));
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripText.ftl", "Monitored Trip Text Email"
        ));
        testCases.add(new TemplateRenderingTestCase(
            notificationsData, "MonitoredTripHtml.ftl", "Monitored Trip HTML Email"
        ));
        return testCases;
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
    private static <T>void assertMatchesSnapshot(String actual, String snapshotName) {
        String actualWithStandardLineSeparators = actual.replaceAll("\r\n", "\n");
        assertThat(actualWithStandardLineSeparators, matchesSnapshot(snapshotName));
    }
}