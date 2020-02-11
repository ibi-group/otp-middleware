package org.opentripplanner.middleware.spark;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.opentripplanner.middleware.BasicOtpDispatcher;

@WebSocket
public class BasicOtpWebSocketController {
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
        // messageToSession(session, message);

        // For Middleware we want to forward the requests right away.
        BasicOtpDispatcher.executeRequestsAsync(msg ->
                messageToSession(session, msg)
        );
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        System.out.println("Closed web socket with " + session.getRemoteAddress() + " reason: " + reason + " (" + statusCode + ")");
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        System.out.println("Received from " + session.getRemoteAddress() + ": " + message);
    }
}
