package org.opentripplanner.middleware.spark;

import org.opentripplanner.middleware.BasicOtpDispatcher;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {
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
    }
}
