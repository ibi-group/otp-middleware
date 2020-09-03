package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Util methods for obtaining information from the YAML configuration file for otp-middleware
 */
public class ConfigUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

    private static final String DEFAULT_ENV = "configurations/default/env.yml";

    private static final String JAR_PREFIX = "otp-middleware-";

    // ObjectMapper that loads in YAML config files
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private static JsonNode envConfig;

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar(String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    /**
     * Check if running in Travis CI. A list of default environment variables from Travis is here:
     * https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
     */
    public static boolean isRunningCi() {
        return getBooleanEnvVar("TRAVIS") && getBooleanEnvVar("CONTINUOUS_INTEGRATION");
    }

    /**
     * Load config files from either program arguments or (if no args specified) from
     * default configuration file locations. Config fields are retrieved with getConfigProperty.
     */
    public static void loadConfig(String[] args) throws IOException {
        FileInputStream envConfigStream;
        if (isRunningCi()) {
            return;
        }
        if (args.length == 0) {
            LOG.warn("Using default env.yml: {}", DEFAULT_ENV);
            envConfigStream = new FileInputStream(new File(DEFAULT_ENV));
        } else {
            LOG.info("Loading env.yml: {}", args[0]);
            envConfigStream = new FileInputStream(new File(args[0]));
        }

        envConfig = yamlMapper.readTree(envConfigStream);
    }

    /**
     * Convenience function to get a config property (nested fields defined by dot notation "data.use_s3_storage") as
     * JsonNode. Checks env.yml and returns null if property is not found.
     */
    private static JsonNode getConfigProperty(String name) {
        String[] parts = name.split("\\.");
        JsonNode node = envConfig;
        for (String part : parts) {
            if (node == null) {
                return null;
            }
            node = node.get(part);
        }
        return node;
    }

    /**
     * Convenience function to check existence of a config property (nested fields defined by dot notation
     * "data.use_s3_storage") in env.yml.
     */
    public static boolean hasConfigProperty(String name) {
        if (isRunningCi()) return System.getenv(name) != null;
        // try the server config first, then the main config
        return hasConfigProperty(envConfig, name);
    }

    /**
     * Returns true if the given config has the requested property.
     *
     * @param config The root config object
     * @param name The desired property in dot notation (ex: "data.use_s3_storage")
     */
    private static boolean hasConfigProperty(JsonNode config, String name) {
        String[] parts = name.split("\\.");
        JsonNode node = config;
        for (String part : parts) {
            if (node == null) {
                return false;
            }
            node = node.get(part);
        }
        return node != null;
    }

    /**
     * Get a config property (nested fields defined by dot notation "data.use_s3_storage") as text.
     */
    public static String getConfigPropertyAsText(String name) {
        if (isRunningCi()) return System.getenv(name);
        JsonNode node = getConfigProperty(name);
        if (node != null) {
            return node.asText();
        } else {
            LOG.warn("Config property {} not found! Returning null.", name);
            return null;
        }
    }

    /**
     * @return a config value (nested fields defined by dot notation "data.use_s3_storage") as text or the default value
     * if the config value is not defined (null).
     */
    public static String getConfigPropertyAsText(String name, String defaultValue) {
        if (isRunningCi()) {
            String value = System.getenv(name);
            return value == null ? defaultValue : value;
        }
        JsonNode node = getConfigProperty(name);
        if (node != null) {
            return node.asText();
        } else {
            LOG.warn("Config property {} not found. Using defaultValue: {}.", name, defaultValue);
            return defaultValue;
        }
    }

    /**
     * @return a config value (nested fields defined by dot notation "data.use_s3_storage") as an int or the default
     * value if the config value is not defined (null) or cannot be converted to an int.
     */
    public static int getConfigPropertyAsInt(String name, int defaultValue) {
        int value = defaultValue;
        try {
            String string = getConfigPropertyAsText(name);
            value = Integer.parseInt(string);
        } catch (NumberFormatException | NullPointerException e) {
            LOG.error("Unable to parse {}. Using default: {}", name, defaultValue, e);
        }
        return value;
    }

    /**
     * Extracts the version number from the JAR file name
     * (modified from https://stackoverflow.com/questions/14189162/get-name-of-running-jar-or-exe#19045510).
     * TODO: Extract git properties from JAR, see
     * https://github.com/ibi-group/datatools-server/blob/9f74b821cf351efcdaf7c9c93a3ae8b694d3c3b1/src/main/java/com/conveyal/datatools/manager/DataManager.java#L181-L212.
     */
    public static String getVersionFromJar() {
        String path = OtpMiddlewareMain.class.getResource(OtpMiddlewareMain.class.getSimpleName() + ".class").getFile();
        // Detect if this is run from a compiled JAR or loose class files from an IDE.
        boolean isUnpackagedClass = path.startsWith(File.separator);

        if (isUnpackagedClass) {
            return "Local Build";
        } else {
            String jarPath = path.substring(0, path.lastIndexOf('!'));
            String jarName = jarPath.substring(jarPath.lastIndexOf(File.separatorChar) + 1);
            String version = jarName.substring(JAR_PREFIX.length(), jarName.lastIndexOf(".jar"));
            if (version.length() == 0) version = "No Version Info";

            return version;
        }
    }
}
