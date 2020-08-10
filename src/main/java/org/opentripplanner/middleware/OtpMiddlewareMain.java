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
import org.opentripplanner.middleware.otp.OtpRequestProcessor;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.trip_monitor.jobs.MonitorAllTripsJob;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Main class for OTP Middleware application. This handles loading the configuration files, initializing the MongoDB
 * persistence, and booting up the Spark HTTP service and its endpoints.
 */
public class OtpMiddlewareMain {
    private static final Logger LOG = LoggerFactory.getLogger(OtpMiddlewareMain.class);
    public static boolean inTestEnvironment;
    private static final String API_PREFIX = "/api/";

    public static void main(String[] args) throws IOException {
        // Load configuration.
        ConfigUtils.loadConfig(args);

        // Connect to MongoDB.
        Persistence.initialize();

        initializeHttpEndpoints();

        // Schedule Bugsnag jobs to start retrieving Bugsnag event and project information
        BugsnagJobs.initialize();

        // Initialize Bugsnag in order to report application errors
        BugsnagReporter.initializeBugsnagErrorReporting();

        // Schedule recurring Monitor All Trips Job.
        // TODO: Determine whether this should go in some other process.
        if (!inTestEnvironment) {
            MonitorAllTripsJob monitorAllTripsJob = new MonitorAllTripsJob();
            Scheduler.scheduleJob(
                monitorAllTripsJob,
                0,
                1,
                TimeUnit.MINUTES
            );
        }
    }

    private static void initializeHttpEndpoints() throws IOException {
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
                    new OtpUserController(API_PREFIX)
                    // TODO Add other models.
                ))
                .generateDoc();

            OtpRequestProcessor.register(spark);
            // Add log controller HTTP endpoints
            // TODO: We should determine whether we want to use Spark Swagger for these endpoints too.
            LogController.register(spark, API_PREFIX);

            // Add Bugsnag controller HTTP endpoints
            BugsnagController.register(spark, API_PREFIX);
        } catch (RuntimeException e) {
            LOG.error("Error initializing API controllers", e);
            System.exit(1);
        }
        spark.options("/*",
            (request, response) -> {
                logMessageAndHalt(request, HttpStatus.OK_200, "OK");
                return "OK";
            });

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
}
