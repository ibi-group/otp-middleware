package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zenika.snapshotmatcher.SnapshotMatcher.matchesSnapshot;
import static org.hamcrest.MatcherAssert.assertThat;

public class TemplateUtilsTest extends OtpMiddlewareTest {

    @BeforeAll
    public static void setup() {
        TestUtils.mockOtpServer();
    }

    /**
     * A parameterized test that checks whether various templates render in a way that matches a snapshot.
     */
    @ParameterizedTest
    @MethodSource("createTemplateRenderingTestCases")
    public void canRenderTemplates(TemplateRenderingTestCase testCase) throws Exception {
        assertThat(TemplateUtils.renderTemplate(testCase.templatePath, testCase.data), matchesSnapshot());
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
        testCases.add(new TemplateRenderingTestCase(errorEventsData, "EventErrorsText.ftl"));
        testCases.add(new TemplateRenderingTestCase(errorEventsData, "EventErrorsHtml.ftl"));
        // Trip Monitor Notifications tests (for CheckMonitoredTrip#sendNotifications).
        Map<String, Object> notificationsData = Map.of(
            "tripId", "18f642d5-f7a8-475a-9469-800129e6c0b3",
            "notifications", List.of("Test notification.", "Another notification.")
        );
        testCases.add(new TemplateRenderingTestCase(notificationsData, "MonitoredTripSms.ftl"));
        testCases.add(new TemplateRenderingTestCase(notificationsData, "MonitoredTripText.ftl"));
        testCases.add(new TemplateRenderingTestCase(notificationsData, "MonitoredTripHtml.ftl"));
        return testCases;
    }

    private static class TemplateRenderingTestCase {
        public final Object data;
        public final String templatePath;

        private TemplateRenderingTestCase(Object data, String templatePath) {
            this.data = data;
            this.templatePath = templatePath;
        }

        @Override public String toString() {
            return templatePath;
        }
    }
}