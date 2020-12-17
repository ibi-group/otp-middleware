package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.opentripplanner.middleware.utils.YamlUtils.yamlMapper;

/**
 * Util methods for obtaining information from the YAML configuration file for otp-middleware
 */
public class ConfigUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

    public static final String DEFAULT_ENV = "configurations/default/env.yml";
    public static final String DEFAULT_ENV_SCHEMA = "configurations/default/env.schema.json";

    private static final String JAR_PREFIX = "otp-middleware-";

    /**
     * Check if running in Travis CI. A list of default environment variables from Travis is here:
     * https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
     */
    public static final boolean isRunningCi = getBooleanEnvVar("TRAVIS") && getBooleanEnvVar("CONTINUOUS_INTEGRATION");

    private static JsonNode envConfig;

    /**
     * Returns true only if an environment variable exists and is set to "true".
     */
    public static boolean getBooleanEnvVar(String var) {
        String variable = System.getenv(var);
        return variable != null && variable.equals("true");
    }

    /**
     * Load config files from either program arguments or (if no args specified) from
     * default configuration file locations. Config fields are retrieved with getConfigProperty.
     */
    public static void loadConfig(String[] args) throws IOException {
        FileInputStream envConfigStream;
        // Check if running in Travis CI. If so, skip loading config (CI uses Travis environment variables).
        if (isRunningCi) return;
        if (args.length == 0) {
            LOG.warn("Using default env.yml: {}", DEFAULT_ENV);
            envConfigStream = new FileInputStream(new File(DEFAULT_ENV));
        } else {
            LOG.info("Loading env.yml: {}", args[0]);
            envConfigStream = new FileInputStream(new File(args[0]));
        }
        envConfig = yamlMapper.readTree(envConfigStream);
        validateConfig();
    }

    /**
     * Validate the environment configuration against the environment configuration schema. If the config is not available,
     * does not match the schema or an exception is thrown, exit the application.
     */
    private static void validateConfig() {
        if ("false".equals(getConfigPropertyAsText("VALIDATE_ENVIRONMENT_CONFIG", "true"))) {
            LOG.warn("Environment configuration schema validation disabled.");
            return;
        }
        try {
            if (envConfig == null) {
                throw new IllegalArgumentException("Environment configuration not available to validate!");
            }
            FileInputStream envSchemaStream = new FileInputStream(new File(DEFAULT_ENV_SCHEMA));
            JsonNode envSchema = yamlMapper.readTree(envSchemaStream);
            if (envSchema == null) {
                throw new IllegalArgumentException("Environment configuration schema not available.");
            }
            ProcessingReport report = JsonSchemaFactory
                    .byDefault()
                    .getValidator()
                    .validate(envSchema, envConfig);
            if (!report.isSuccess()) {
                throw new IllegalArgumentException(report.toString());
            }
        } catch (IOException | IllegalArgumentException | ProcessingException e) {
            LOG.error("Unable to validate environment configuration.", e);
            System.exit(1);
        }
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
        // Check if running in Travis CI. If so, use Travis environment variables instead of config file.
        if (isRunningCi) return System.getenv(name) != null;
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
        // Check if running in Travis CI. If so, use Travis environment variables instead of config file.
        if (isRunningCi) return System.getenv(name);
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
        // Check if running in Travis CI. If so, use Travis environment variables instead of config file.
        if (isRunningCi) {
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
            LOG.warn("Unable to parse {}. Using default: {}", name, defaultValue);
        }
        return value;
    }

    /**
     * Extracts the version number from the JAR file name
     * (modified from https://stackoverflow.com/questions/14189162/get-name-of-running-jar-or-exe#19045510).
     * TODO: Extract git properties from JAR, see
     * https://github.com/ibi-group/datatools-server/blob/9f74b821cf351efcdaf7c9c93a3ae8b694d3c3b1/src/main/java/com/conveyal/datatools/manager/DataManager.java#L181-L212.
     *
     * "/" is used instead of File.separator, see
     * https://stackoverflow.com/questions/24749007/how-to-use-file-separator-for-a-jar-file-resource/24749976#24749976
     *
     * In a Windows environment the file separator is "\" which always fails when comparing to a resource which is "/".
     */
    public static String getVersionFromJar() {
        String path = OtpMiddlewareMain.class.getResource(OtpMiddlewareMain.class.getSimpleName() + ".class").getFile();
        // Detect if this is run from a compiled JAR or loose class files from an IDE.
        boolean isUnpackagedClass = path.startsWith("/");

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
