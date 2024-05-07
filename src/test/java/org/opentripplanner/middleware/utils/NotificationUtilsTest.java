package org.opentripplanner.middleware.utils;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.utils.NotificationUtils.PUSH_TOTAL_MAX_LENGTH;
import static org.opentripplanner.middleware.utils.NotificationUtils.PUSH_TITLE_MAX_LENGTH;
import static org.opentripplanner.middleware.utils.NotificationUtils.getPushDevicesUrl;
import static org.opentripplanner.middleware.utils.NotificationUtils.getTwilioLocale;

/**
 * Contains tests for the various notification utilities.
 * Tests in this file do not require specific environment variables
 * and do not contain end-to-end notification actions.
 */
class NotificationUtilsTest {

    public static final String SHORT_TITLE = "Short title";
    public static final String SHORT_BODY = "Short body";

    @Test
    void canGetTwilioLocale() {
        assertEquals("en-GB", getTwilioLocale("en-GB"));
        assertEquals("fr", getTwilioLocale("fr-FR"));
        assertEquals("zh", getTwilioLocale("zh"));
        assertEquals("zh-HK", getTwilioLocale("zh-HK"));
        assertEquals("pt", getTwilioLocale("pt"));
        assertEquals("pt-BR", getTwilioLocale("pt-BR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "e", "en", "en-US"})
    @NullSource
    void twilioLocaleDefaultsToEnglish(String locale) {
        assertEquals("en", getTwilioLocale(locale));
    }

    @Test
    void testGetPushDevicesUrl() {
        String email = "first.last+suffix@example.com";
        String base = "https://get.example.com/devices/?user=";
        assertEquals(
            "https://get.example.com/devices/?user=first.last%2Bsuffix%40example.com",
            getPushDevicesUrl(base, email)
        );
    }

    @ParameterizedTest
    @MethodSource("createNotificationInfoCases")
    void testTruncateNotificationPayload(String originalTitle, String expectedTitle, String originalMessage, String expectedMessage, String message) {
        NotificationUtils.NotificationInfo info = new NotificationUtils.NotificationInfo(
            "user@example.com",
            originalMessage,
            originalTitle,
            "trip-id"
        );

        // For Android, the title must be truncated to fit character limit.
        assertEquals(expectedTitle, info.title, String.format("%s - should truncate title if needed", message));
        assertTrue(info.title.length() <= PUSH_TITLE_MAX_LENGTH);
        assertEquals(expectedMessage, info.message, String.format("%s - should truncate body if needed", message));

        int actualLength = info.message.length() + info.title.length();
        assertTrue(
            actualLength <= PUSH_TOTAL_MAX_LENGTH,
            String.format("%s - total length of truncated content should fit iOS limit", message)
        );
    }

    static Stream<Arguments> createNotificationInfoCases() {
        return Stream.of(
            Arguments.of(
                SHORT_TITLE,
                SHORT_TITLE,
                SHORT_BODY,
                SHORT_BODY,
                "Short title and body"
            ),
            Arguments.of(
                SHORT_TITLE,
                SHORT_TITLE,
                StringUtils.repeat('a', 200),
                StringUtils.repeat('a', PUSH_TOTAL_MAX_LENGTH - SHORT_TITLE.length()),
                "Short title, long body"
            ),
            Arguments.of(
                StringUtils.repeat('t', 200),
                StringUtils.repeat('t', PUSH_TITLE_MAX_LENGTH),
                SHORT_BODY,
                SHORT_BODY,
                "Long title, short body"
            ),
            Arguments.of(
                StringUtils.repeat('a', 200),
                StringUtils.repeat('a', PUSH_TITLE_MAX_LENGTH),
                StringUtils.repeat('b', 200),
                StringUtils.repeat('b', PUSH_TOTAL_MAX_LENGTH - PUSH_TITLE_MAX_LENGTH),
                "Long title, long body"
            )
        );
    }
}
