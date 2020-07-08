package org.opentripplanner.middleware.spark;

import com.beerboy.ss.SparkSwagger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.controllers.api.AdminUserController;
import org.opentripplanner.middleware.controllers.api.ApiUserController;
import org.opentripplanner.middleware.controllers.api.TripHistoryController;
import org.opentripplanner.middleware.otp.OtpRequestProcessor;
import org.opentripplanner.middleware.controllers.api.OtpUserController;
import org.opentripplanner.middleware.persistence.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

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

        initializeHttpEndpoints();
    }

    private static void initializeHttpEndpoints() throws IOException {
        // Must start spark explicitly to use spark-swagger.
        // https://github.com/manusant/spark-swagger#endpoints-binding
        Service spark = Service.ignite().port(Service.SPARK_DEFAULT_PORT);

        // websocket() must be declared before the other get() endpoints.
        // available at http://localhost:4567/async-websocket
        spark.webSocket("/async-websocket", BasicOtpWebSocketController.class);
        try {
            SparkSwagger.of(spark)
                // Register API routes.
                .endpoints(() -> List.of(
                    new AdminUserController(API_PREFIX),
                    new ApiUserController(API_PREFIX),
                    new OtpUserController(API_PREFIX)
                    // TODO Add other models.
                ))
                .generateDoc();
            OtpRequestProcessor.register(spark);
        } catch (RuntimeException e) {
            LOG.error("Error initializing API controllers", e);
            System.exit(1);
        }
        spark.options("/*",
            (request, response) -> {
                logMessageAndHalt(request, HttpStatus.OK_200, "OK");
                return "OK";
            });

        // available at http://localhost:4567/hello
        spark.get("/hello", (req, res) -> "(Sparks) OTP Middleware says Hi!");

        // available at http://localhost:4567/sync
        spark.get("/sync", (req, res) -> BasicOtpDispatcher.executeRequestsInSequence());

        // available at http://localhost:4567/async
        spark.get("/async", (req, res) -> BasicOtpDispatcher.executeRequestsAsync());

        // available at http://localhost:4567/api/secure/triprequests
        spark.get(API_PREFIX + "/secure/triprequests", TripHistoryController::getTripRequests);

        spark.before(API_PREFIX + "/secure/*", ((request, response) -> {
            if (!request.requestMethod().equals("OPTIONS")) Auth0Connection.checkUser(request);
        }));
        spark.before(API_PREFIX + "admin/*", ((request, response) -> {
            if (!request.requestMethod().equals("OPTIONS")) {
                Auth0Connection.checkUserIsAdmin(request, response);
            }
        }));

        // Return "application/json" and set gzip header for all API routes.
        spark.before(API_PREFIX + "*", (request, response) -> {
            response.type("application/json"); // Handled by API response documentation. If specified, "Try it out" feature in API docs fails.
            response.header("Content-Encoding", "gzip");
        });

        /////////////////    Final API routes     /////////////////////

        // Return 404 for any API path that is not configured.
        // IMPORTANT: Any API paths must be registered before this halt.
        spark.get(API_PREFIX + "*", (request, response) -> {
            logMessageAndHalt(request, 404, "No API route configured for this path.");
            return null;
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
        String[] parts = name.split("\\.");
        JsonNode node = envConfig;
        for (String part : parts) {
            if (node == null) {
                LOG.warn("Config property {} not found", name);
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
            if (node == null) return false;
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
