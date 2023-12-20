package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.opentripplanner.middleware.utils.NotificationUtils.getPushDevicesUrl;
import static org.opentripplanner.middleware.utils.NotificationUtils.getTwilioLocale;

/**
 * Contains tests for the various notification utilities.
 * Tests in this file do not require specific environment variables
 * and do not contain end-to-end notification actions.
 */
class NotificationUtilsTest {
    @Test
    void canGetTwilioLocale() {
        Assertions.assertEquals("en-GB", getTwilioLocale("en-GB"));
        Assertions.assertEquals("fr", getTwilioLocale("fr-FR"));
        Assertions.assertEquals("zh", getTwilioLocale("zh"));
        Assertions.assertEquals("zh-HK", getTwilioLocale("zh-HK"));
        Assertions.assertEquals("pt", getTwilioLocale("pt"));
        Assertions.assertEquals("pt-BR", getTwilioLocale("pt-BR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "e", "en", "en-US"})
    @NullSource
    void twilioLocaleDefaultsToEnglish(String locale) {
        Assertions.assertEquals("en", getTwilioLocale(locale));
    }

    @Test
    void testGetPushDevicesUrl() {
        String email = "first.last+suffix@example.com";
        String base = "https://get.example.com/devices/?user=";
        Assertions.assertEquals(
            "https://get.example.com/devices/?user=first.last%2Bsuffix%40example.com",
            getPushDevicesUrl(base, email)
        );
    }
}
