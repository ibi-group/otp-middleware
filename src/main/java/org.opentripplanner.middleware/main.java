package org.opentripplanner.middleware;

import static spark.Spark.get;

class Main {
    public static void main(String[] args) {
        // Define some endpoints,
        // available at http://localhost:4567/hello
        get("/hello", (req, res) -> "OTP Middleware says Hi!");

        // available at http://localhost:4567/sync
        get("/sync", (req, res) -> BasicOtpDispatcher.executeRequestsInSequence());

        // available at http://localhost:4567/async
        get("/async", (req, res) -> BasicOtpAsyncDispatcher.executeRequestsAsync());
    }
}
