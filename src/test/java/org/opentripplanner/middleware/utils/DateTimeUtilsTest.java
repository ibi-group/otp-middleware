package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDaysBetween;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getHoursBetween;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getPreviousDayFrom;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getPreviousWholeHourFrom;

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

    @Test
    void canGetPreviousDay() {
        var date = LocalDateTime.of(2024, 8, 10, 15, 34, 17);
        var expectedDate = LocalDateTime.of(2024, 8, 9, 0, 0, 0);
        assertEquals(expectedDate, getPreviousDayFrom(date));
    }

    @Test
    void canGetPreviousWholeHour() {
        var date = LocalDateTime.of(2024, 8, 10, 15, 34, 17);
        var expectedDate = LocalDateTime.of(2024, 8, 10, 14, 0, 0);
        assertEquals(expectedDate, getPreviousWholeHourFrom(date));
    }

    @Test
    void canGetDaysBetween() {
        var date1 = LocalDateTime.of(2024, 8, 10, 15, 34, 17);
        var date2 = LocalDateTime.of(2024, 8, 15, 9, 55, 32);
        var expectedDays = List.of(
            LocalDateTime.of(2024, 8, 11, 0, 0, 0),
            LocalDateTime.of(2024, 8, 12, 0, 0, 0),
            LocalDateTime.of(2024, 8, 13, 0, 0, 0),
            LocalDateTime.of(2024, 8, 14, 0, 0, 0)
        );
        assertEquals(expectedDays, getDaysBetween(date1, date2));
    }

    @Test
    void canGetHoursBetween() {
        var date1 = LocalDateTime.of(2024, 8, 10, 20, 34, 17);
        var date2 = LocalDateTime.of(2024, 8, 11, 3, 55, 32);
        var expectedHours = List.of(
            LocalDateTime.of(2024, 8, 10, 21, 0, 0),
            LocalDateTime.of(2024, 8, 10, 22, 0, 0),
            LocalDateTime.of(2024, 8, 10, 23, 0, 0),
            LocalDateTime.of(2024, 8, 11, 0, 0, 0),
            LocalDateTime.of(2024, 8, 11, 1, 0, 0),
            LocalDateTime.of(2024, 8, 11, 2, 0, 0)
        );
        assertEquals(expectedHours, getHoursBetween(date1, date2));
    }
}
