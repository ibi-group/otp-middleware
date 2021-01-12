package org.opentripplanner.middleware.docs;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


import static org.opentripplanner.middleware.utils.JsonUtils.*;
import static org.opentripplanner.middleware.utils.YamlUtils.yamlMapper;

/**
 * This class is solely intended to be executed on a development machine to update the version-controlled
 * env.schema.json values section of {@link #LATEST_README_FILE}.
 */
public class ReadMeEnvSchemaValuesUpdater {
    public static final String LATEST_README_FILE = "README.md";
    public static final String ENV_SCHEMA_JSON_TITLE = "### env.schema.json values";

    private static final Logger LOG = LoggerFactory.getLogger(ReadMeEnvSchemaValuesUpdater.class);

    public static void main(String[] args) throws IOException {
        updateEnvSchemaValues();
    }

    /**
     * Produce the env schema json values markdown content for {@link #LATEST_README_FILE}.
     */
    public static String generateEnvSchemaValuesContent() throws IOException {
        JsonNode envSchema = yamlMapper.readTree(ReadMeEnvSchemaValuesUpdater.class.getClassLoader().getResourceAsStream(ConfigUtils.DEFAULT_ENV_SCHEMA));
        if (envSchema == null) {
            throw new IllegalArgumentException("env.schema.json not available to update README.md.");
        }
        StringBuilder tableData = new StringBuilder("| Key | Type | Required | Example | Description |");
        tableData.append(System.lineSeparator())
            .append("| --- | --- | --- | --- | --- |")
            .append(System.lineSeparator());
        Iterator<String> propertyNames = envSchema.get("properties").fieldNames();
        List<String> requiredProperties = (envSchema.get("required") != null)
            ? getPOJOFromJSONAsList(envSchema.get("required"), String.class)
            : Collections.emptyList();
        JsonNode properties = envSchema.get("properties");
        while (propertyNames.hasNext()) {
            String propertyName = propertyNames.next();
            JsonNode property = properties.get(propertyName);
            String type = getJSONPropertyFieldAsString(property, "type");
            String required = (requiredProperties
                .stream()
                .anyMatch(propertyName::equals))
                ? "Required"
                : "Optional";
            String fieldValue = getJSONPropertyFieldAsString(property, "examples");
            List<String> examples = getPOJOFromJSONAsList(fieldValue, String.class);
            if (examples == null)
                examples = Collections.emptyList();
            String description = getJSONPropertyFieldAsString(property, "description");
            tableData.append("| ")
                .append(propertyName)
                .append(" | ")
                .append(type.replaceAll("\"", ""))
                .append(" | ")
                .append(required)
                .append(" | ")
                .append(String.join(", ", examples))
                .append(" | ")
                .append(description.replaceAll("\"", ""))
                .append(" |")
                .append(System.lineSeparator());
        }
        return tableData.toString();
    }

    /**
     * Updates the {@link #LATEST_README_FILE} file with the latest env.schema.json values. It is expected that the
     * title for this section be named '### env.schema.json values' and be the last section in the README.md file. Any
     * sections after this will be removed as part of this update process!
     */
    private static void updateEnvSchemaValues() throws IOException {
        Path readMeFile = new File(LATEST_README_FILE).toPath();
        String readMeContent = Files.readString(readMeFile);
        int envSchemaJsonTitleIndex = readMeContent.indexOf(ENV_SCHEMA_JSON_TITLE);
        if (envSchemaJsonTitleIndex == -1) {
            throw new IllegalArgumentException(String.format("Could not find %s header!", ENV_SCHEMA_JSON_TITLE));
        }
        readMeContent = readMeContent.substring(0, envSchemaJsonTitleIndex + ENV_SCHEMA_JSON_TITLE.length());
        readMeContent += System.lineSeparator();
        readMeContent += generateEnvSchemaValuesContent();
        Files.writeString(readMeFile, readMeContent);
        LOG.info("The version-controlled README.md file has been updated.");
    }
}
