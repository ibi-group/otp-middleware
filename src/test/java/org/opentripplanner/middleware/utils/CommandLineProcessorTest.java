package org.opentripplanner.middleware.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.recurringjobs.RecurringJob;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.utils.CommandLineProcessor.LOAD_BALANCE_ERROR_MESSAGE;
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
            assertEquals(commandLineTestCase.isLoadBalanced, processor.isLoadBalance());
        } catch (IllegalArgumentException e) {
            assertEquals(commandLineTestCase.errorMessage, e.getMessage());
        }
    }

    private static Stream<CommandLineTestCase> createCommandLineParametersCases() {
        return Stream.of(
            new CommandLineTestCase()
                .withCommand(new String[] {"Unknown", "set", "of", "commands"})
                .withMessage("Unknown command, should default to original command outcome: default env, all jobs and load balancing."),
            new CommandLineTestCase()
                .withCommand(new String[] {})
                .withMessage("Original command with default env, all jobs and load balancing."),
            new CommandLineTestCase()
                .withCommand(new String[] {TEST_ENV})
                .withConfigFile(TEST_ENV)
                .withMessage("Original command with test env, all jobs and load balancing."),
            new CommandLineTestCase()
                .withCommand(new String[] {"-loadbalance", "no"})
                .withMessage("Original command, all jobs and no load balancing.")
                .withLoadBalanced(false),
            new CommandLineTestCase()
                .withCommand(new String[] {"-loadbalance", "yes"})
                .withMessage("Original command, all jobs and with explicit load balancing.")
                .withLoadBalanced(true),
            new CommandLineTestCase()
                .withCommand(new String[] {"-loadbalance", "no", "-recurringjobs", "monitor-all-trips-job", "trip-history-upload-job"})
                .withMessage("Two jobs and no load balancing.")
                .withLoadBalanced(false)
                .withRecurringJob(Set.of(RecurringJob.MONITOR_ALL_TRIPS_JOB, RecurringJob.TRIP_HISTORY_UPLOAD_JOB)),
            new CommandLineTestCase()
                .withCommand(new String[] {"-loadbalance", "no", "-recurringjobs", "monitor-all-trips-job", "trip-history-upload-job", "bugsnag-event-handing-job", "trip-survey-sender-job", "trip-survey-upload-job"})
                .withMessage("All jobs long hand and no load balancing.")
                .withLoadBalanced(false)
                .withRecurringJob(RecurringJob.getAllRecurringJobs()),
            new CommandLineTestCase()
                .withCommand(new String[] {"-loadbalance", "off"})
                .withMessage("Unknown load balancing, throw exception.")
                .withErrorMessage(LOAD_BALANCE_ERROR_MESSAGE),
            new CommandLineTestCase()
                .withCommand(new String[] {"-recurringjobs", "unknown-job"})
                .withMessage("Unknown job, throw exception.")
                .withErrorMessage(CommandLineProcessor.getRecurringJobErrorMessage("unknown-job"))
        );
    }

    private static class CommandLineTestCase {
        public String configFile = DEFAULT_ENV;
        public Set<RecurringJob> recurringJobs = RecurringJob.getAllRecurringJobs();
        public boolean isLoadBalanced = true;
        public String[] command;
        public String errorMessage;
        public String message;

        public CommandLineTestCase withConfigFile(String configFile) {
            this.configFile = configFile;
            return this;
        }

        public CommandLineTestCase withRecurringJob(Set<RecurringJob> recurringJobs) {
            this.recurringJobs = recurringJobs;
            return this;
        }

        public CommandLineTestCase withLoadBalanced(boolean isLoadBalanced) {
            this.isLoadBalanced = isLoadBalanced;
            return this;
        }

        public CommandLineTestCase withCommand(String[] command) {
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