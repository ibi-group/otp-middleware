package org.opentripplanner.middleware.spring;

import org.json.JSONObject;
import org.opentripplanner.middleware.BasicOtpDispatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class BasicOtpWebSocketController extends TextWebSocketHandler {
    private static void messageToSession(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IllegalStateException ise) {
            System.out.println("Spring Websocket: Waiting legal state before sending: " + message);
            try {
                Thread.sleep(200); // FIXME with something better.
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            messageToSession(session, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        BasicOtpDispatcher.executeRequestsAsync(msg ->
            messageToSession(session, msg)
        );
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message)
            throws InterruptedException, IOException {

        String payload = message.getPayload();
        JSONObject jsonObject = new JSONObject(payload);
        session.sendMessage(new TextMessage("Hi " + jsonObject.get("user") + " how may we help you?"));
    }
}
