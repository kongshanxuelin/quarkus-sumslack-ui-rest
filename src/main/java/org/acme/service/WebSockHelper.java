package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.acme.dto.WebSocketMessage;
import org.acme.websocket.WebSocketSessionManager;

import java.util.Map;

@ApplicationScoped
public class WebSockHelper {
    @Inject
    WebSocketSessionManager sessionManager;
    public void broadcast(Map<String,Object> message){
        WebSocketMessage webSocketMessage = new WebSocketMessage();
        webSocketMessage.setType("broadcast");
        JsonObject jsonObject = Json.createObjectBuilder(message).build();
        webSocketMessage.setPayload(jsonObject.toString());
        sessionManager.broadcast(webSocketMessage);
    }
}
