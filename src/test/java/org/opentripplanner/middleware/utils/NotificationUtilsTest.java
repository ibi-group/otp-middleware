package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.opentripplanner.middleware.utils.NotificationUtils.getTwilioLocale;

/**
 * Contains tests for the various notification utilities.
 * Tests in this file do not require specific environment variables
 * and do not contain end-to-end notification actions.
 */
class NotificationUtilsTest {
    @Test
    void canGetTwilioLocale() {
        Assertions.assertEquals("en", getTwilioLocale("en"));
        Assertions.assertEquals("en", getTwilioLocale("en-US"));
        Assertions.assertEquals("en-GB", getTwilioLocale("en-GB"));
        Assertions.assertEquals("fr", getTwilioLocale("fr-FR"));
        Assertions.assertEquals("zh", getTwilioLocale("zh"));
        Assertions.assertEquals("zh-HK", getTwilioLocale("zh-HK"));
        Assertions.assertEquals("pt", getTwilioLocale("pt"));
        Assertions.assertEquals("pt-BR", getTwilioLocale("pt-BR"));
    }
}
