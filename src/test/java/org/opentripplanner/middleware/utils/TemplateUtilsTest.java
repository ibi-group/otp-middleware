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
        assertThat(TemplateUtils.renderTemplateWithData(testCase.templatePath, testCase.data), matchesSnapshot());
    }

    /**
     * Creates the template rendering test cases. If there are any templates that are used in the main code, they should
     * always be tested here to make sure their snapshot and mock data are able to render what is expected. Intermediate
     * files that templates inherit from don't need to be tested individually.
     */
    public static List<TemplateRenderingTestCase> createTemplateRenderingTestCases() {
        List<TemplateRenderingTestCase> testCases = new ArrayList<>();

        // Event Errors (this is a template for an email that is sent in BugsnagEventHandlingJob#sendEmailForEvents)
        Map<String, String> data = Map.of("subject", "test subject");
        testCases.add(new TemplateRenderingTestCase(data, "EventErrorsText.ftl"));
        testCases.add(new TemplateRenderingTestCase(data, "EventErrorsHtml.ftl"));

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