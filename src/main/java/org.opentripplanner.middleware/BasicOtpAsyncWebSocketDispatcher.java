package org.opentripplanner.middleware;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@WebSocket
public class BasicOtpAsyncWebSocketDispatcher {
    private static void messageToSession(Session session, String message) {
        try {
            session.getRemote().sendString(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) throws Exception {
        String message = "Established web socket connection with " + session.getRemoteAddress();

        System.out.println(message);
        messageToSession(session, message);

        // For Middleware we want to forward the requests right away.
        executeRequestsAsyncWebSocket(session);
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        System.out.println("Closed web socket with " + session.getRemoteAddress() + " reason: " + reason + " (" + statusCode + ")");
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        System.out.println("Received from " + session.getRemoteAddress() + ": " + message);
    }

    /**
     *
     */
    public static void executeRequestsAsyncWebSocket(Session session) {
        long startTime = System.currentTimeMillis();

        Map<Integer, String> urls = IntStream.range(0, Const.urls.length)
                .boxed()
                .collect(Collectors.toMap(i -> i, i -> Const.urls[i]));

        // Good stuff from https://openjdk.java.net/groups/net/httpclient/recipes.html
        HttpClient client = HttpClient.newHttpClient();
        Map<Integer, CompletableFuture<HttpResponse<String>>> requests = urls.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(e.getValue())).build();

                    CompletableFuture<HttpResponse<String>> result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                    result.thenApply(r -> {
                        long time = System.currentTimeMillis() - startTime;

                        String log = "Response " + e.getKey() + " received in " + time + " ms.<br/>\n";
                        System.out.println(log);

                        messageToSession(session, log);
                        return log;
                    });
                    return result;
                }));

        CompletableFuture.allOf(requests.values().toArray(new CompletableFuture<?>[0]))
                .join();

        long totalTime = System.currentTimeMillis() - startTime;

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        messageToSession(session, "Completed in " + totalTime + " ms.");
    }
}
