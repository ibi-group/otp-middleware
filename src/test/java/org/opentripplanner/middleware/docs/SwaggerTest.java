package org.opentripplanner.middleware.docs;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.middleware.docs.PublicApiDocGenerator.getSwaggerDocs;
import static org.opentripplanner.middleware.docs.VersionControlledSwaggerUpdater.LATEST_SWAGGER_FILE;

/**
 * Contains a test to verify that the swagger docs are generated correctly
 * and that there are no undocumented changes to the API endpoints.
 */
public class SwaggerTest extends OtpMiddlewareTestEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(SwaggerTest.class);

    /**
     * Verify that {@link VersionControlledSwaggerUpdater#LATEST_SWAGGER_FILE} matches the latest changes to the API as generated by spark-swagger
     * (available at {@link PublicApiDocGenerator#getSwaggerDocs()}.
     * If there are changes to the API, run {@link VersionControlledSwaggerUpdater#main} to update the file.
     */
    @Test
    public void swaggerDocsAreUpToDate() throws IOException {
        HttpResponseValues swaggerResponse = getSwaggerDocs();
        assertEquals(HttpStatus.OK_200, swaggerResponse.status);
        String autoGeneratedSwagger = swaggerResponse.responseBody;
        JsonNode swaggerJson = YamlUtils.yamlMapper.readTree(autoGeneratedSwagger);
        String title = swaggerJson.get("info").get("title").asText();
        LOG.info("Found swagger docs title: {}", title);
        assertEquals(title, "OTP Middleware");

        // When ran in GitHub Actions the localhost address isn't used and instead some odd IP address shows as the
        // host. Therefore, this line of code replaces the host with localhost:4567 during this test case assertion.
        autoGeneratedSwagger = autoGeneratedSwagger.replaceAll("host: .*4567\"", "host: \"localhost:4567\"");

        Path versionControlledSwaggerFile = new File(LATEST_SWAGGER_FILE).toPath();
        String versionControlledSwagger = Files.readString(versionControlledSwaggerFile);
        assertEquals(versionControlledSwagger, autoGeneratedSwagger,
            String.format(
                "If you modified any API endpoint or configuration, please also run VersionControlledSwaggerUpdater#main and commit %s.",
                LATEST_SWAGGER_FILE
            )
        );
    }
}
