package org.opentripplanner.middleware.spark;

import com.beerboy.ss.SparkSwagger;
import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.opentripplanner.middleware.controllers.api.ApiControllerImpl;
import org.opentripplanner.middleware.persistence.Persistence;
import spark.Service;

import java.io.IOException;
import java.util.List;

import static spark.Spark.*;

public class Main {
    private static final String API_PREFIX = "/api/";
    public static final boolean DO_SWAGGER = true;

    public static void main(String[] args) throws IOException {
        // Connect to MongoDB.
        Persistence.initialize();

        if (DO_SWAGGER) {
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
                            new ApiControllerImpl(API_PREFIX, Persistence.users)
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
        else {
            // websocket() must be declared before the other get() endpoints.
            // available at http://localhost:4567/async-websocket
            webSocket("/async-websocket", BasicOtpWebSocketController.class);

            // available at http://localhost:4567/hello
            get("/", (req, res) -> "(Sparks) OTP Middleware says Hi!");

            // available at http://localhost:4567/sync
            get("/sync", (req, res) -> BasicOtpDispatcher.executeRequestsInSequence());

            // available at http://localhost:4567/async
            get("/async", (req, res) -> BasicOtpDispatcher.executeRequestsAsync());

            // Register API routes.
            new ApiControllerImpl(API_PREFIX, Persistence.users);
            // // TODO Add other models.

            before(API_PREFIX + "secure/*", ((request, response) -> {
                // TODO Add Auth0 authentication to requests.
    //            Auth0Connection.checkUser(request);
    //            Auth0Connection.checkEditPrivileges(request);
            }));

            // Return "application/json" and set gzip header for all API routes.
            before(API_PREFIX + "*", (request, response) -> {
                response.type("application/json"); // Handled by API response documentation. If specified, "Try it out" feature in API docs fails.
                response.header("Content-Encoding", "gzip");
            });
        }
    }
}
