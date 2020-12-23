package org.opentripplanner.middleware.docs;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.docs.ReadMeEnvSchemaValuesUpdater.ENV_SCHEMA_JSON_TITLE;
import static org.opentripplanner.middleware.docs.ReadMeEnvSchemaValuesUpdater.LATEST_README_FILE;

/**
 * Contains a test to verify that the env.schema.json values section of {@link ReadMeEnvSchemaValuesUpdater#LATEST_README_FILE}
 * matches the {@Link ConfigUtils.ENV_SCHEMA} file content once formatted as markdown.
 */
public class ReadMeEnvSchemaValuesTest {

    /**
     * Verify that {@link ReadMeEnvSchemaValuesUpdater#LATEST_README_FILE} matches the latest env.schema.json file
     * changes. If there are changes to the env.schema.json file, run {@link ReadMeEnvSchemaValuesUpdater#main} to
     * update the env.schema.json values section of {@link ReadMeEnvSchemaValuesUpdater#LATEST_README_FILE}.
     */
    @Test
    public void envSchemaValuesAreUpToDate() throws IOException {
        String expectedEnvSchemaValues = ReadMeEnvSchemaValuesUpdater
            .generateEnvSchemaValuesContent()
            .trim();
        Path readMeFile = new File(LATEST_README_FILE).toPath();
        String readMeContent = Files.readString(readMeFile);
        int endOfEnvSchemaJsonTitle = readMeContent.indexOf(ENV_SCHEMA_JSON_TITLE) + ENV_SCHEMA_JSON_TITLE.length();
        String actualEnvSchemaValues = readMeContent
            .substring(endOfEnvSchemaJsonTitle)
            .trim();
        assertEquals(expectedEnvSchemaValues, actualEnvSchemaValues,
            String.format(
                "If you have modified the env.schema.json file, please also run ReadMeEnvSchemaValuesUpdater#main and commit %s.",
                LATEST_README_FILE
            )
        );
    }
}