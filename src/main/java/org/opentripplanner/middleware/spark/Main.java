package org.opentripplanner.middleware.spark;

import com.google.gson.Gson;
import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.opentripplanner.middleware.persistence.Persistence;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {
        Gson gson = new Gson();
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

        get("/users", (req, res) -> Persistence.users.getAll(), v -> gson.toJson(v));
//        delete("/users", (req, res) -> Persistence.users.getAll(), v -> gson.toJson(v));
        post("/users", (req, res) -> Persistence.users.create(req.body()), v -> gson.toJson(v));
//        put("/users", (req, res) -> Persistence.users.create(req.body()), v -> gson.toJson(v));
    }
}
