package org.opentripplanner.middleware.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateTimeUtilsTest {
    @ParameterizedTest
    @MethodSource("createDateFormatCases")
    void supportsDateFormatsInSeveralLocales(String localeTag, String result) {
        ZonedDateTime zonedTime = ZonedDateTime.of(2023, 2, 12, 17, 44, 0, 0, DateTimeUtils.getOtpZoneId());
        assertEquals(result, DateTimeUtils.formatShortDate(
            Date.from(zonedTime.toInstant()),
            Locale.forLanguageTag(localeTag))
        );
    }

    private static Stream<Arguments> createDateFormatCases() {
        return Stream.of(
            Arguments.of("en-US", "5:44 PM"),
            Arguments.of("fr", "17:44"),
            Arguments.of("es", "17:44"),
            Arguments.of("ko", "오후 5:44"),
            Arguments.of("vi", "17:44"),
            Arguments.of("zh", "下午5:44"), // Note: The Format.JS library shows 24-hour format for Chinese.
            Arguments.of("ru", "17:44"),
            Arguments.of("tl", "5:44 PM")
        );
    }
}
