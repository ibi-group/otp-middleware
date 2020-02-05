package org.opentripplanner.middleware;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BasicOtpAsyncDispatcher {

    /**
     *
     * @return Example response:
     * [Response 7 received in 1242 ms.
     * Response 6 received in 1259 ms.
     * Response 8 received in 1264 ms.
     * Response 0 received in 1381 ms.
     * Response 1 received in 1724 ms.
     * Response 2 received in 1903 ms.
     * Response 5 received in 2007 ms.
     * Response 3 received in 2732 ms.
     * ]
     * Completed in 3082 ms.
     */
    public static String executeRequestsAsync() {
        long startTime = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        Map<Integer, String> urls = IntStream.range(0, Const.urls.length)
                .boxed()
                .collect(Collectors.toMap(i -> i, i -> Const.urls[i]));

        // Good stuff from https://openjdk.java.net/groups/net/httpclient/recipes.html
        HttpClient client = HttpClient.newHttpClient();
        Map<Integer, CompletableFuture<HttpResponse<String>>> requests = urls.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(e.getValue())).build();

                    return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                }));

        CompletableFuture.allOf(requests.entrySet().stream()
                .peek(res -> res.getValue().thenApply(r -> {
                    long time = System.currentTimeMillis() - startTime;

                    String log = "Response " + res.getKey() + " received in " + time + " ms.<br/>\n";
                    System.out.println(log);
                    sb.append(log);
                    return log;
                }))
                .map(Map.Entry::getValue)
                .toArray(CompletableFuture<?>[]::new))
                .join();

        long totalTime = System.currentTimeMillis() - startTime;

        sb
                .append("]")
                .append("<br/>")
                .append("Completed in ")
                .append(totalTime)
                .append(" ms.");

        return sb.toString();
    }
}
