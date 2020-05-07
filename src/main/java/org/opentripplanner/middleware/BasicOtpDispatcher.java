package org.opentripplanner.middleware;

import spark.Request;
import spark.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class BasicOtpDispatcher {

    /**
     * @return Example response:
     * Response 0 received in 1097 ms.
     * Response 1 received in 1712 ms.
     * Response 2 received in 2475 ms.
     * Response 3 received in 4101 ms.
     * Response 4 received in 6158 ms.
     * Response 5 received in 6265 ms.
     * Response 6 received in 6452 ms.
     * Response 7 received in 6648 ms.
     * Response 8 received in 6879 ms.
     * Completed in 6879 ms.
     */
    public static String executeRequestsInSequence() {
        long startTime = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < Const.urls.length; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(Const.urls[i])).build();
            try {
                long start = System.currentTimeMillis();

                // This call is blocking.
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                recordResponse(sb::append, start, i);
            } catch (InterruptedException | IOException e) {
                System.out.println("Error with request " + i);
                e.printStackTrace();
            }
        }

        sb.append(getTotalTimeString(System.currentTimeMillis() - startTime));
        return sb.toString();
    }

    /**
     *
     * @return Example response:
     * Response 6 received in 1189 ms.
     * Response 7 received in 1194 ms.
     * Response 8 received in 1223 ms.
     * Response 0 received in 1331 ms.
     * Response 1 received in 1624 ms.
     * Response 5 received in 1641 ms.
     * Response 2 received in 1849 ms.
     * Response 3 received in 2696 ms.
     * Response 4 received in 3102 ms.
     * Completed in 3103 ms.
     */
    public static String executeRequestsAsync() {
        StringBuilder sb = new StringBuilder();
        executeRequestsAsync(sb::append);
        return sb.toString();
    }

    /**
     *
     */
    public static void executeRequestsAsync(Consumer<String> sendMessageFn) {
        long startTime = System.currentTimeMillis();

        // Good stuff from https://openjdk.java.net/groups/net/httpclient/recipes.html
        HttpClient client = HttpClient.newHttpClient();

        CompletableFuture<?>[] requests = IntStream.range(0, Const.urls.length)
                .boxed()
                .map(i -> {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(Const.urls[i])).build();
                    return client
                            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(r -> {
                                recordResponse(sendMessageFn, startTime, i);
                                return r;
                            });
                })
                .toArray(CompletableFuture<?>[]::new);

        // This call is blocking.
        // It will wait for all requests to return.
        CompletableFuture.allOf(requests).join();

        long totalTime = System.currentTimeMillis() - startTime;

        // Wait for all the response writing to finish
        // before writing the closing text.
        try {
            Thread.sleep(500);
            sendMessageFn.accept(getTotalTimeString(totalTime));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String getTotalTimeString(long totalTime) {
        return "Completed in " + totalTime + " ms.";
    }

    private static void recordResponse(Consumer<String> sendMessageFn, long startTime, Integer index) {
        long time = System.currentTimeMillis() - startTime;

        String log = "Response " + index + " received in " + time + " ms.<br/>\n";
        System.out.println(log);
        sendMessageFn.accept(log);
    }

    public void processTripRequest(Request req, Response res) {

    }
}
