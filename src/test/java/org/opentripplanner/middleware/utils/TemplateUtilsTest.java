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

    @ParameterizedTest
    @MethodSource("createEmailRenderingTestCases")
    public void canRenderEmailTemplates(EmailRenderingTestCase testCase) throws Exception {
        assertThat(TemplateUtils.renderTemplateWithData(testCase.templatePath, testCase.data), matchesSnapshot());
    }

    public static List<EmailRenderingTestCase> createEmailRenderingTestCases() {
        List<EmailRenderingTestCase> testCases = new ArrayList<>();

        // Event Errors
        Map<String, String> data = Map.of("subject", "test subject");
        testCases.add(new EmailRenderingTestCase(data, "EventErrorsText.ftl"));
        testCases.add(new EmailRenderingTestCase(data, "EventErrorsHtml.ftl"));

        return testCases;
    }

    private static class EmailRenderingTestCase {
        public final Object data;
        public final String templatePath;

        private EmailRenderingTestCase(Object data, String templatePath) {
            this.data = data;
            this.templatePath = templatePath;
        }

        @Override public String toString() {
            return templatePath;
        }
    }
}