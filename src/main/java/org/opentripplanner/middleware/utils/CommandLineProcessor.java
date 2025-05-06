package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.recurringjobs.RecurringJob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import static org.opentripplanner.middleware.utils.ConfigUtils.DEFAULT_ENV;

/**
 * Parses the command line to extract which jobs and/or services should be started. The only stipulation, is that if a
 * config file is defined, it must be the first argument defined. e.g.
 *
 * /configurations/test/env.yml --endpoints-only yes --recurring-jobs monitor-all-trips-job
 */
public class CommandLineProcessor {
    public static final List<String> END_POINTS_ONLY_FLAGS = List.of("--endpoints-only", "-E");
    public static final List<String> RECURRING_JOB_FLAGS = List.of("--recurring-jobs", "-R");
    public static final String END_POINTS_ERROR_MESSAGE = String.format("Invalid value for %s. Use 'yes' or 'no'.", END_POINTS_ONLY_FLAGS);
    public static final String RECURRING_JOB_ERROR_MESSAGE = "Unknown recurring job: %s. Valid jobs are: %s";

    private final Set<RecurringJob> recurringJobs;
    private boolean hadEndPoints;
    private String configFile;

    public CommandLineProcessor() {
        this.recurringJobs = RecurringJob.getAllRecurringJobs();
        this.hadEndPoints = true;
        this.configFile = DEFAULT_ENV;
    }

    /**
     * Parse the arguments provided on the command line. To maintain backward compatibility, if a config file is
     * required, it must be the first argument and no command e.g. -config is NOT required.
     */
    public void parseArguments(String[] arguments) {
        if (arguments.length > 0 && arguments[0].contains(".yml")) {
            configFile = arguments[0];
        }
        Map<String, Set<String>> groupedParams = parseCommandLineArguments(arguments);
        groupedParams.forEach((flag, commandValues) -> {
            if (RECURRING_JOB_FLAGS.contains(flag)) {
                defineRecurringJobs(commandValues);
            }
            if (END_POINTS_ONLY_FLAGS.contains(flag)) {
                defineEndPoints(commandValues);
            }
        });
    }

    /**
     * Group commands and command arguments.
     */
    private static Map<String, Set<String>> parseCommandLineArguments(String[] args) {
        Map<String, Set<String>> groupedParams = new HashMap<>();
        String currentKey = null;

        for (String arg : args) {
            if (arg.startsWith("-")) {
                currentKey = arg;
                groupedParams.put(currentKey, new HashSet<>());
            } else if (currentKey != null) {
                groupedParams.get(currentKey).add(arg);
            }
        }
        return groupedParams;
    }

    /**
     * Define which recurring jobs should be enabled.
     */
    private void defineRecurringJobs(Set<String> recurringJobArguments) {
        recurringJobs.clear();
        for (String job : recurringJobArguments) {
            RecurringJob recurringJob = RecurringJob.getJobFromCommandLineArgument(job);
            if (recurringJob != null) {
                recurringJobs.add(recurringJob);
            } else {
                throw new IllegalArgumentException(String.format(
                    RECURRING_JOB_ERROR_MESSAGE,
                    job,
                    RecurringJob.getAllCommandLineNames()
                ));
            }
        }
    }

    /**
     * Define if the end points should be enabled.
     */
    private void defineEndPoints(Set<String> endPointArguments) {
        if (endPointArguments.size() == 1 && endPointArguments.contains("yes")) {
            hadEndPoints = true;
        } else if (endPointArguments.size() == 1 && endPointArguments.contains("no")) {
            hadEndPoints = false;
        } else {
            throw new IllegalArgumentException(END_POINTS_ERROR_MESSAGE);
        }
    }

    public Set<RecurringJob> getRecurringJobs() {
        return recurringJobs;
    }

    public boolean hasEndPoints() {
        return hadEndPoints;
    }

    public String getConfigFile() {
        return configFile;
    }

    public static String getRecurringJobErrorMessage(String unknownJobName) {
        return String.format(RECURRING_JOB_ERROR_MESSAGE, unknownJobName, RecurringJob.getAllCommandLineNames());
    }
}
