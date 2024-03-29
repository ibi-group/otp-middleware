package org.opentripplanner.middleware.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

class DateTimeUtilsTest {
    @ParameterizedTest
    @MethodSource("createDateFormatCases")
    void supportsDateFormatsInSeveralLocales(String localeTag, String pattern) {
        ZonedDateTime zonedTime = ZonedDateTime.of(2023, 2, 12, 17, 44, 0, 0, DateTimeUtils.getOtpZoneId());
        assertThat(
            DateTimeUtils.formatShortDate(Date.from(zonedTime.toInstant()), Locale.forLanguageTag(localeTag)),
            matchesPattern(pattern)
        );
    }

    private static Stream<Arguments> createDateFormatCases() {
        // JDK 20 uses narrow no-break space U+202F before "PM" for time format; earlier JDKs just use a space.
        // Also, JDK 20 uses 24-hour format for Chinese (as does Format.JS library); earlier JDKs use "下午".
        return Stream.of(
            Arguments.of("en-US", "5:44[\\u202f ]PM"),
            Arguments.of("fr", "17:44"),
            Arguments.of("es", "17:44"),
            Arguments.of("ko", "오후 5:44"),
            Arguments.of("vi", "17:44"),
            Arguments.of("zh", "(17|下午5):44"),
            Arguments.of("ru", "17:44"),
            Arguments.of("tl", "5:44[\\u202f ]PM")
        );
    }
}
