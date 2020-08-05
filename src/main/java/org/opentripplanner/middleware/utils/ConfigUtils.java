package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.opentripplanner.middleware.utils.YamlUtils.yamlMapper;

public class ConfigUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigUtils.class);
    public static final String DEFAULT_ENV = "configurations/default/env.yml";
    private static JsonNode envConfig;

    /**
     * Load config files from either program arguments or (if no args specified) from
     * default configuration file locations. Config fields are retrieved with getConfigProperty.
     */
    public static void loadConfig(String[] args) throws IOException {
        FileInputStream envConfigStream;
        if (args.length == 0) {
            LOG.warn("Using default env.yml: {}", DEFAULT_ENV);
            envConfigStream = new FileInputStream(new File(DEFAULT_ENV));
        }
        else {
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
     * "data.use_s3_storage") in either server.yml or env.yml.
     */
    public static boolean hasConfigProperty(String name) {
        // try the server config first, then the main config
        return hasConfigProperty(envConfig, name);
    }

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
            JsonNode node = getConfigProperty(name);
            value = Integer.parseInt(node.asText());
        } catch (NumberFormatException | NullPointerException e) {
            LOG.error("Unable to parse {}. Using default: {}", name, defaultValue, e);
        }
        return value;
    }
}
