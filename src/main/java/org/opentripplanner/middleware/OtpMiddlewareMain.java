package org.opentripplanner.middleware;

import com.beerboy.ss.SparkSwagger;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.bugsnag.BugsnagJobs;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.controllers.api.AdminUserController;
import org.opentripplanner.middleware.controllers.api.ApiUserController;
import org.opentripplanner.middleware.controllers.api.BugsnagController;
import org.opentripplanner.middleware.controllers.api.OtpUserController;
import org.opentripplanner.middleware.controllers.api.LogController;
import org.opentripplanner.middleware.controllers.api.MonitoredTripController;
import org.opentripplanner.middleware.controllers.api.TripHistoryController;
import org.opentripplanner.middleware.docs.PublicApiDocGenerator;
import org.opentripplanner.middleware.otp.OtpRequestProcessor;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Main class for OTP Middleware application. This handles loading the configuration files, initializing the MongoDB
 * persistence, and booting up the Spark HTTP service and its endpoints.
 */
public class OtpMiddlewareMain {
    private static final Logger LOG = LoggerFactory.getLogger(OtpMiddlewareMain.class);
    private static final String API_PREFIX = "/api/";
    public static boolean inTestEnvironment = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        // Load configuration.
        ConfigUtils.loadConfig(args);

        // Connect to MongoDB.
        Persistence.initialize();

        initializeHttpEndpoints();

        if (!inTestEnvironment) {
            // Schedule Bugsnag jobs to start retrieving Bugsnag event and project information
            BugsnagJobs.initialize();

            // Initialize Bugsnag in order to report application errors
            BugsnagReporter.initializeBugsnagErrorReporting();
        }
    }

    private static void initializeHttpEndpoints() throws IOException, InterruptedException {
        // Must start spark explicitly to use spark-swagger.
        // https://github.com/manusant/spark-swagger#endpoints-binding
        Service spark = Service.ignite().port(Service.SPARK_DEFAULT_PORT);
        try {
            SparkSwagger.of(spark)
                // Register API routes.
                .endpoints(() -> List.of(
                    new AdminUserController(API_PREFIX),
                    new ApiUserController(API_PREFIX),
                    new MonitoredTripController(API_PREFIX),
                    new TripHistoryController(API_PREFIX),
                    new OtpUserController(API_PREFIX),
                    new LogController(API_PREFIX),
                    new BugsnagController(API_PREFIX),
                    new OtpRequestProcessor()
                    // TODO Add other models.
                ))
                // Spark-swagger auto-generates a swagger document at localhost:4567/doc.yaml.
                // (That path is not configurable.)
                .generateDoc();
        } catch (RuntimeException e) {
            LOG.error("Error initializing API controllers", e);
            System.exit(1);
        }

        // Generate the public facing API docs after startup,
        // and create an undocumented endpoint to serve the document.
        Path publicDocPath = new PublicApiDocGenerator().generatePublicApiDocs();
        spark.get("/docs", (request, response) -> {
            response.type("text/yaml");
            return Files.readString(publicDocPath);
        });

        // Generic response for all OPTIONS requests on all endpoint paths.
        spark.options("/*",
            (request, response) -> {
                logMessageAndHalt(request, HttpStatus.OK_200, "OK");
                return "OK";
            });

        // Security checks for admin and /secure/ endpoints.
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
            response.type(APPLICATION_JSON);
            response.header("Content-Encoding", "gzip");
        });

        /////////////////    Final API routes     /////////////////////

        // Return 404 for any API path that is not configured.
        // IMPORTANT: Any API paths must be registered before this halt.
        spark.get(API_PREFIX + "*", (request, response) -> {
            logMessageAndHalt(
                request,
                404,
                String.format("No API route configured for path %s.", request.uri())
            );
            return null;
        });
    }
}
