package org.opentripplanner.middleware.spark;

import com.beerboy.ss.SparkSwagger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.opentripplanner.middleware.controllers.api.ApiControllerImpl;
import org.opentripplanner.middleware.models.User;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String DEFAULT_ENV = "configurations/default/env.yml";
    private static JsonNode envConfig;
    // ObjectMapper that loads in YAML config files
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final String API_PREFIX = "/api/";

    public static void main(String[] args) throws IOException {
        // Load configuration.
        loadConfig(args);
        // Connect to MongoDB.
        Persistence.initialize();

        // Must start spark explicitly to use spark-swagger.
        // https://github.com/manusant/spark-swagger#endpoints-binding
        Service spark = Service.ignite().port(Service.SPARK_DEFAULT_PORT);

        // Define some endpoints.
        // spark.staticFileLocation("/public");

        // websocket() must be declared before the other get() endpoints.
        // available at http://localhost:4567/async-websocket
        spark.webSocket("/async-websocket", BasicOtpWebSocketController.class);


        SparkSwagger.of(spark)
                // Register API routes.
                .endpoints(() -> List.of(
                        new ApiControllerImpl<User>(API_PREFIX, Persistence.users)
                        // TODO Add other models.
                ))
                .generateDoc();

        // available at http://localhost:4567/hello
        spark.get("/", (req, res) -> "(Sparks) OTP Middleware says Hi!");

        // available at http://localhost:4567/sync
        spark.get("/sync", (req, res) -> BasicOtpDispatcher.executeRequestsInSequence());

        // available at http://localhost:4567/async
        spark.get("/async", (req, res) -> BasicOtpDispatcher.executeRequestsAsync());

        spark.before(API_PREFIX + "secure/*", ((request, response) -> {
            // TODO Add Auth0 authentication to requests.
//            Auth0Connection.checkUser(request);
//            Auth0Connection.checkEditPrivileges(request);
        }));

        // Return "application/json" and set gzip header for all API routes.
        spark.before(API_PREFIX + "*", (request, response) -> {
            response.type("application/json"); // Handled by API response documentation. If specified, "Try it out" feature in API docs fails.
            response.header("Content-Encoding", "gzip");
        });
    }

    /**
     * Load config files from either program arguments or (if no args specified) from
     * default configuration file locations. Config fields are retrieved with getConfigProperty.
     */
    private static void loadConfig(String[] args) throws IOException {
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
        String parts[] = name.split("\\.");
        JsonNode node = envConfig;
        for (int i = 0; i < parts.length; i++) {
            if(node == null) {
                LOG.warn("Config property {} not found", name);
                return null;
            }
            node = node.get(parts[i]);
        }
        return node;
    }

    /**
     * Get a config property (nested fields defined by dot notation "data.use_s3_storage") as text.
     */
    public static String getConfigPropertyAsText(String name) {
        JsonNode node = getConfigProperty(name);
        if (node != null) {
            return node.asText();
        } else {
            LOG.warn("Config property {} not found", name);
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
            return defaultValue;
        }
    }
}
