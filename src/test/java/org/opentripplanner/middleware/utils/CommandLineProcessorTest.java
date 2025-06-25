package org.opentripplanner.middleware.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.recurringjobs.RecurringJob;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.utils.CommandLineProcessor.END_POINTS_ONLY_FLAGS;
import static org.opentripplanner.middleware.utils.CommandLineProcessor.RECURRING_JOB_FLAGS;
import static org.opentripplanner.middleware.utils.ConfigUtils.DEFAULT_ENV;

class CommandLineProcessorTest {
    public static final String TEST_ENV = "configurations/test/env.yml";

    @ParameterizedTest
    @MethodSource("createCommandLineParametersCases")
    void canParseCommandLineParameters(CommandLineTestCase commandLineTestCase) {
        try {
            CommandLineProcessor processor = new CommandLineProcessor();
            processor.parseArguments(commandLineTestCase.command);
            assertEquals(commandLineTestCase.configFile, processor.getConfigFile(), commandLineTestCase.message);
            assertEquals(commandLineTestCase.recurringJobs, processor.getRecurringJobs());
            assertEquals(commandLineTestCase.hasEndpoints, processor.hasEndPoints());
        } catch (IllegalArgumentException e) {
            assertEquals(commandLineTestCase.errorMessage, e.getMessage());
        }
    }

    private static Stream<CommandLineTestCase> createCommandLineParametersCases() {
        final String endPointFlagLonghand = END_POINTS_ONLY_FLAGS.get(0);
        final String endPointFlagShorthand = END_POINTS_ONLY_FLAGS.get(1);
        final String recurringJobFlagLonghand = RECURRING_JOB_FLAGS.get(0);
        final String recurringJobFlagShorthand = RECURRING_JOB_FLAGS.get(1);

        return Stream.of(
            new CommandLineTestCase()
                .withCommand("Unknown", "set", "of", "commands")
                .withMessage("Unknown commands, default env, all jobs and no end points."),
            new CommandLineTestCase()
                .withCommand()
                .withMessage("Default config, all jobs and no end points."),
            new CommandLineTestCase()
                .withCommand(TEST_ENV, endPointFlagLonghand)
                .withConfigFile(TEST_ENV)
                .withEndpoints(true)
                .withMessage("Test config, all jobs and end points."),
            new CommandLineTestCase()
                .withCommand(endPointFlagShorthand, "no")
                .withEndpoints(false)
                .withMessage("Default config, all jobs and explicit 'no' end points."),
            new CommandLineTestCase()
                .withCommand(endPointFlagShorthand, "yes")
                .withEndpoints(true)
                .withMessage("Default config, all jobs and explicit 'yes' end points."),
            new CommandLineTestCase()
                .withCommand(endPointFlagShorthand)
                .withEndpoints(true)
                .withMessage("Default config, all jobs and with 'shorthand' end points."),
            new CommandLineTestCase()
                .withCommand(endPointFlagLonghand)
                .withEndpoints(true)
                .withMessage("Default config, all jobs and with 'longhand' end points."),
            new CommandLineTestCase()
                .withCommand(recurringJobFlagLonghand, "monitor-all-trips-job", "trip-history-upload-job")
                .withRecurringJobs(Set.of(RecurringJob.MONITOR_ALL_TRIPS_JOB, RecurringJob.TRIP_HISTORY_UPLOAD_JOB))
                .withMessage("Two jobs and no end points."),
            new CommandLineTestCase()
                .withCommand(endPointFlagLonghand, recurringJobFlagLonghand, "none")
                .withEndpoints(true)
                .withRecurringJobs(Set.of())
                .withMessage("End points and no jobs."),
            new CommandLineTestCase()
                .withCommand(recurringJobFlagLonghand, "monitor-all-trips-job", "trip-history-upload-job", "bugsnag-event-handing-job", "trip-survey-sender-job", "trip-survey-upload-job")
                .withRecurringJobs(RecurringJob.getAllRecurringJobs())
                .withMessage("All jobs long hand and no end points."),
            new CommandLineTestCase()
                .withCommand(recurringJobFlagShorthand, "unknown-job")
                .withErrorMessage(CommandLineProcessor.getRecurringJobErrorMessage("unknown-job"))
                .withMessage("Unknown job, throw exception."),
            new CommandLineTestCase()
                .withCommand(endPointFlagLonghand, "off")
                .withErrorMessage(CommandLineProcessor.getEndPointsOnlyErrorMessage("[off]"))
                .withMessage("Unknown argument, throw exception.")
        );
    }

    private static class CommandLineTestCase {
        public String configFile = DEFAULT_ENV;
        public Set<RecurringJob> recurringJobs = RecurringJob.getAllRecurringJobs();
        public boolean hasEndpoints = false;
        public String[] command;
        public String errorMessage;
        public String message;

        public CommandLineTestCase withConfigFile(String configFile) {
            this.configFile = configFile;
            return this;
        }

        public CommandLineTestCase withRecurringJobs(Set<RecurringJob> recurringJobs) {
            this.recurringJobs = recurringJobs;
            return this;
        }

        public CommandLineTestCase withEndpoints(boolean hasEndpoints) {
            this.hasEndpoints = hasEndpoints;
            return this;
        }

        public CommandLineTestCase withCommand(String... command) {
            this.command = command;
            return this;
        }

        public CommandLineTestCase withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public CommandLineTestCase withMessage(String message) {
            this.message = message;
            return this;
        }

        @Override
        public String toString() {
            return message;
        }
    }
}