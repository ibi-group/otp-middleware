package org.opentripplanner.middleware.docs;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.utils.YamlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SwaggerTest extends OtpMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(SwaggerTest.class);

    @Test
    public void swaggerDocsAreUpToDate() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest get = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:4567/doc.yaml"))
            .GET()
            .build();
        HttpResponse<String> response = client.send(get, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.statusCode(), 200);
        String autoGeneratedSwagger = response.body();
        JsonNode swaggerJson = YamlUtils.yamlMapper.readTree(autoGeneratedSwagger);
        String title = swaggerJson.get("info").get("title").asText();
        LOG.info("Found swagger docs title: {}", title);
        assertEquals(title, "OTP Middleware");
        File versionControlledSwaggerFile = new File("src/main/resources/doc.yaml");
        String versionControlledSwagger = Files.readString(versionControlledSwaggerFile.toPath());
        assertEquals(autoGeneratedSwagger, versionControlledSwagger);
    }
}
