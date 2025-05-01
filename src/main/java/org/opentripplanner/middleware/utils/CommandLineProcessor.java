package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.recurringjobs.RecurringJob;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import static org.opentripplanner.middleware.utils.ConfigUtils.DEFAULT_ENV;

/**
 * Parses the command line to extract which jobs and/or services should be started. The only stipulation, is that if a
 * config file is defined, it must be the first argument defined. e.g.
 *
 * /configurations/test/env.yml -loadbalance yes -recurringjobs monitor-all-trips-job
 */
public class CommandLineProcessor {
    public static final String LOAD_BALANCE_ERROR_MESSAGE = "Invalid value for -loadbalance. Use 'yes' or 'no'.";
    public static final String RECURRING_JOB_ERROR_MESSAGE = "Unknown recurring job: %s. Valid jobs are: %s";

    private final Set<RecurringJob> recurringJobs;
    private boolean loadBalance;
    private String configFile;

    public CommandLineProcessor() {
        this.recurringJobs = RecurringJob.getAllRecurringJobs();
        this.loadBalance = true;
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
        groupedParams.forEach((command, commandValues) -> {
            if (command.equalsIgnoreCase("-recurringjobs")) {
                defineRecurringJobs(commandValues);
            }
            if (command.equalsIgnoreCase("-loadbalance")) {
                defineLoadBalancing(commandValues);
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
     * Define if load balancing tasks should be enabled.
     */
    private void defineLoadBalancing(Set<String> loadBalancingArguments) {
        if (loadBalancingArguments.size() == 1 && loadBalancingArguments.contains("yes")) {
            loadBalance = true;
        } else if (loadBalancingArguments.size() == 1 && loadBalancingArguments.contains("no")) {
            loadBalance = false;
        } else {
            throw new IllegalArgumentException(LOAD_BALANCE_ERROR_MESSAGE);
        }
    }

    public Set<RecurringJob> getRecurringJobs() {
        return recurringJobs;
    }

    public boolean isLoadBalance() {
        return loadBalance;
    }

    public String getConfigFile() {
        return configFile;
    }

    public static String getRecurringJobErrorMessage(String unknownJobName) {
        return String.format(RECURRING_JOB_ERROR_MESSAGE, unknownJobName, RecurringJob.getAllCommandLineNames());
    }
}
