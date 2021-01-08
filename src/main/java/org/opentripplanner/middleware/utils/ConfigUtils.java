package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotSupportedException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.opentripplanner.middleware.utils.YamlUtils.yamlMapper;

/**
 * Util methods for obtaining information from the YAML configuration file for otp-middleware
 */
public class ConfigUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);

    public static final String DEFAULT_ENV = "configurations/default/env.yml";
    public static final String DEFAULT_ENV_SCHEMA = "env.schema.json";
    private static JsonNode ENV_SCHEMA = null;
    private static final String JAR_PREFIX = "otp-middleware-";

    static {
        // Load in env.yml schema file statically so that it is available for populating properties when running CI.
        try {
            ENV_SCHEMA = yamlMapper.readTree(ConfigUtils.class.getClassLoader().getResourceAsStream(DEFAULT_ENV_SCHEMA));
        } catch (IOException e) {
            LOG.error("Could not read env.yml config schema", e);
            System.exit(1);
        }
    }

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
        // Check if running in Travis CI. If so, skip loading config (CI uses Travis environment variables).
        if (isRunningCi) {
            envConfig = constructConfigFromEnvironment();
        } else if (args.length == 0) {
            LOG.warn("Using default env.yml: {}", DEFAULT_ENV);
            envConfig = yamlMapper.readTree(new FileInputStream(DEFAULT_ENV));
        } else {
            LOG.info("Loading env.yml: {}", args[0]);
            envConfig = yamlMapper.readTree(new FileInputStream(args[0]));
        }
        validateConfig();
    }

    /**
     * Construct a config file from environment variables. If running in CI, the only way to set up the config is via
     * environment variables. This allows us to read in these variables from the CI environment and validate them
     * against the schema file.
     */
    private static JsonNode constructConfigFromEnvironment() {
        ObjectNode config = yamlMapper.createObjectNode();
        for (Iterator<Map.Entry<String, JsonNode>> it = ENV_SCHEMA.get("properties").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> property = it.next();
            String key = property.getKey();
            String type = property.getValue().get("type").asText();
            String value = System.getenv(key);
            if (value == null) continue;
            // Parse value as specified type from schema.
            switch (type) {
                // TODO: Add more types
                case "boolean":
                    config.put(key, Boolean.parseBoolean(value));
                    break;
                case "integer":
                    config.put(key, Integer.parseInt(value));
                    break;
                case "string":
                    config.put(key, value);
                    break;
                default:
                    throw new NotSupportedException(String.format("Config type %s not yet supported by parser!", type));
            }
        }
        return config;
    }

    /**
     * Validate the environment configuration against the environment configuration schema. If the config is not available,
     * does not match the schema or an exception is thrown, exit the application.
     */
    private static void validateConfig() {
        if ("false".equals(getConfigPropertyAsText("VALIDATE_ENVIRONMENT_CONFIG", "true"))) {
            LOG.warn("env.yml schema validation disabled.");
            return;
        }
        try {
            if (envConfig == null) {
                throw new IllegalArgumentException("env.yml not available to validate!");
            }
            // FIXME: Json schema validator (https://github.com/java-json-tools/json-schema-validator) only supports
            //  JSON schema draft v4. We are using JSON schema draft v7 to make use of the "examples" parameter.
            //  When validating this warning is produced: "the following keywords are unknown and will be ignored: [examples]"
            ProcessingReport report = JsonSchemaFactory
                .byDefault()
                .getValidator()
                .validate(ENV_SCHEMA, envConfig);
            if (!report.isSuccess()) {
                throw new IllegalArgumentException(report.toString());
            }
        } catch (IllegalArgumentException | ProcessingException e) {
            LOG.error("Unable to validate env.yml.", e);
            System.exit(1);
        }
    }

    /**
     * Convenience function to get a config property (nested fields defined by dot notation "data.use_s3_storage") as
     * JsonNode. Checks env.yml and returns null if property is not found.
     */
    public static JsonNode getConfigProperty(String name) {
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
        // try the server config first, then the main config
        return hasConfigProperty(envConfig, name);
    }

    /**
     * Returns true if the given config has the requested property.
     *
     * @param config The root config object
     * @param name   The desired property in dot notation (ex: "data.use_s3_storage")
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
