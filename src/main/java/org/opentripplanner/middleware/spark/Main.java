package org.opentripplanner.middleware.spark;

import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.opentripplanner.middleware.controllers.api.ApiControllerImpl;
import org.opentripplanner.middleware.persistence.Persistence;

import static spark.Spark.*;

public class Main {
    private static final String API_PREFIX = "api/";

    public static void main(String[] args) {
        // Connect to the MongoDB
        Persistence.initialize();
        // Define some endpoints,
        staticFileLocation("/public");

        //
        // websocket() must be declared before the other get() endpoints.
        // available at http://localhost:4567/async-websocket
        webSocket("/async-websocket", BasicOtpWebSocketController.class);

        // available at http://localhost:4567/hello
        get("/hello", (req, res) -> "(Sparks) OTP Middleware says Hi!");

        // available at http://localhost:4567/sync
        get("/sync", (req, res) -> BasicOtpDispatcher.executeRequestsInSequence());

        // available at http://localhost:4567/async
        get("/async", (req, res) -> BasicOtpDispatcher.executeRequestsAsync());

        // Register API routes.
        new ApiControllerImpl(API_PREFIX, Persistence.users);
        // TODO Add other models.

        before(API_PREFIX + "secure/*", ((request, response) -> {
            // TODO Add Auth0 authentication to requests.
//            Auth0Connection.checkUser(request);
//            Auth0Connection.checkEditPrivileges(request);
        }));

        // Return "application/json" and set gzip header for all API routes.
        before(API_PREFIX + "*", (request, response) -> {
            response.type("application/json");
            response.header("Content-Encoding", "gzip");
        });
    }
}
