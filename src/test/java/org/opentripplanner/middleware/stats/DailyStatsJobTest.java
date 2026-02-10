package org.opentripplanner.middleware.stats;

import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.stats.DailyStatsJob.DATE_FIELD;
import static org.opentripplanner.middleware.stats.DailyStatsJob.getDateFilter;

class DailyStatsJobTest extends OtpMiddlewareTestEnvironment {

    private static OtpUser user1;
    private static OtpUser user2;
    private static OtpUser user3;

    private static DailyStats stats1;
    private static DailyStats stats2;
    private static DailyStats expectedStats;

    private static final Date DATE_1 = DateTimeUtils.convertToDate(LocalDateTime.of(2025, 10, 24, 12, 0));
    private static final Date DATE_2 = DateTimeUtils.convertToDate(LocalDateTime.of(2025, 10, 25, 1, 0));
    private static final LocalDate DAY_1 = LocalDate.of(2025, 10, 24);

    @BeforeAll
    static void setUp() {
        // Accounts for existing OtpUser objects, including those not removed by other tests.
        long initialOtpUserCount = Persistence.otpUsers.getCount();

        user1 = createUser("user1");
        user2 = createUser("user2");
        user3 = createUser("user3");

        createTripRequest("req1", "batch1", DATE_1, "user1");
        createTripRequest("req2", "batch1", DATE_1, "user1");
        createTripRequest("req3", "batch1", DATE_2, "user1");
        createTripRequest("req4", "batch2", DATE_1, "user1");
        createTripRequest("req5", "batch3", DATE_2, "user1");
        createTripRequest("req6", "batch3", DATE_2, "user1");
        createTripRequest("req7", "batch4", DATE_1, "user2");
        createTripRequest("req8", "batch4", DATE_1, "user2");
        createTripRequest("req9", "batch5", DATE_1, "user2");

        // Used by shouldRetrieveStats
        stats1 = createStats(DAY_1.minusDays(5));
        stats2 = createStats(DAY_1.minusDays(4));

        expectedStats = new DailyStats();
        expectedStats.date = DateTimeUtils.convertToDate(LocalDateTime.of(DAY_1, LocalTime.MIDNIGHT));
        expectedStats.otpUsers = initialOtpUserCount + 3;
        expectedStats.tripRequests = 4;
        expectedStats.otpUsersWithTripRequests = 2;
    }

    @AfterAll
    static void tearDown() {
        // No Auth0 account is created in this test.
        // This will also delete trip requests.
        user1.delete(false);
        user2.delete(false);
        user3.delete(false);

        Persistence.dailyStats.removeById(stats1.id);
        Persistence.dailyStats.removeById(stats2.id);

        assertEquals(0, Persistence.dailyStats.getCount());
    }

    @AfterEach
    void afterEach() {
        DateTimeUtils.useSystemDefaultClockAndTimezone();
    }

    @Test
    void canRetrieveStats() {
        DailyStatsJob job = new DailyStatsJob();
        DailyStats stats = job.retrieveStats(DAY_1);
        assertEquals(expectedStats, stats);
    }

    private static OtpUser createUser(String id) {
        OtpUser user = new OtpUser();
        user.id = id;
        Persistence.otpUsers.create(user);
        return user;
    }

    private static void createTripRequest(String id, String batchId, Date date, String userId) {
        TripRequest request = new TripRequest();
        request.id = id;
        request.batchId = batchId;
        request.userId = userId;
        request.dateCreated = date;
        Persistence.tripRequests.create(request);
    }

    @ParameterizedTest
    @MethodSource("shouldRetrieveStatsCases")
    void shouldRetrieveStats(LocalDate day, boolean expected) {
        DailyStatsJob job = new DailyStatsJob();
        assertEquals(expected, job.shouldRetrieveStats(day));
    }

    private static Stream<Arguments> shouldRetrieveStatsCases() {
        return Stream.of(
            Arguments.of(DAY_1.minusDays(5), false),
            Arguments.of(DAY_1.minusDays(4), false),
            Arguments.of(DAY_1.minusDays(3), true)
        );
    }

    private static DailyStats createStats(LocalDate date) {
        DailyStats stats = new DailyStats();
        stats.id = DateTimeUtils.getStringFromDate(date, DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN);
        stats.date = DateTimeUtils.convertToDate(LocalDateTime.of(date, LocalTime.MIDNIGHT));
        Persistence.dailyStats.create(stats);
        return stats;
    }

    @Test
    void shouldRunJob() {
        DateTimeUtils.useFixedClockAt(DateTimeUtils.makeOtpZonedDateTime(DATE_1).plusDays(1));
        Bson dateFilter = getDateFilter(DAY_1, DATE_FIELD);

        DailyStatsJob job = new DailyStatsJob();
        // Initial run of the job should occur right after scheduling and
        // should end long before the job repeat interval has passed.
        job.scheduleRecurringJob();

        assertEquals(1, Persistence.dailyStats.getCountFiltered(dateFilter));
        DailyStats stats = Persistence.dailyStats.getOneFiltered(dateFilter);

        // Second run of the job should not create another DailyStats entry (id should be the same).
        job.run();
        assertEquals(1, Persistence.dailyStats.getCountFiltered(dateFilter));
        DailyStats statsAfter = Persistence.dailyStats.getOneFiltered(dateFilter);
        assertEquals(stats.id, statsAfter.id);

        Persistence.dailyStats.removeById(stats.id);

        assertEquals(expectedStats, stats);
    }
}
