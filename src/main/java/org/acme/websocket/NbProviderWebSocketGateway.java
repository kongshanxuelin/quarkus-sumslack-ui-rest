package org.acme.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.acme.dto.WebSocketMessage;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * NB Provider WebSocket 网关端点
 * <p>
 * 连接地址: /nb-provider-websocket-gateway/gateway3
 * <p>
 * 接收的报文格式:
 * <pre>
 * {
 *   "type": "消息类型",
 *   "payload": { ... }
 * }
 * </pre>
 * <p>
 * 根据消息的 type 字段进行分发处理，并将响应结果推送回客户端。
 */
@ServerEndpoint("/nb-provider-websocket-gateway/gateway3")
@ApplicationScoped
public class NbProviderWebSocketGateway {

    private static final Logger LOG = Logger.getLogger(NbProviderWebSocketGateway.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    WebSocketSessionManager sessionManager;

    /**
     * 客户端连接建立时触发
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        sessionManager.addSession(session);
        LOG.infof("WebSocket connection opened: sessionId=%s, queryParams=%s",
                session.getId(), session.getRequestParameterMap());

        // 向客户端发送连接成功确认消息
        WebSocketMessage welcome = new WebSocketMessage("connected", Map.of(
                "sessionId", session.getId(),
                "message", "连接成功"
        ));
        sessionManager.sendToSession(session.getId(), welcome);
    }

    /**
     * 收到客户端消息时触发
     */
    @OnMessage
    public void onMessage(Session session, String message) {
        LOG.debugf("Received message from session %s: %s", session.getId(), message);

        // 解析消息
        WebSocketMessage wsMessage = sessionManager.deserialize(message);
        if (wsMessage == null) {
            // 解析失败，返回错误消息
            WebSocketMessage error = new WebSocketMessage("error", Map.of(
                    "code", "INVALID_FORMAT",
                    "message", "消息格式错误，请发送包含 type 和 payload 字段的 JSON"
            ));
            sessionManager.sendToSession(session.getId(), error);
            return;
        }

        String type = wsMessage.getType();
        if (type == null || type.isBlank()) {
            WebSocketMessage error = new WebSocketMessage("error", Map.of(
                    "code", "MISSING_TYPE",
                    "message", "消息缺少 type 字段"
            ));
            sessionManager.sendToSession(session.getId(), error);
            return;
        }

        // 根据 type 分发处理
        WebSocketMessage response = dispatch(session, type, wsMessage.getPayload());
        if (response != null) {
            sessionManager.sendToSession(session.getId(), response);
        }
    }

    /**
     * 客户端连接关闭时触发
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessionManager.removeSession(session);
        LOG.infof("WebSocket connection closed: sessionId=%s, reason=%s",
                session.getId(), closeReason.getReasonPhrase());
    }

    /**
     * 发生错误时触发
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        LOG.errorf(throwable, "WebSocket error on session %s", session.getId());
        sessionManager.removeSession(session);
    }

    /**
     * 根据消息类型分发处理
     * <p>
     * 在此处扩展新的消息类型处理逻辑。
     *
     * @param session 当前会话
     * @param type    消息类型
     * @param payload 消息负载
     * @return 响应消息，返回 null 表示不回复
     */
    private WebSocketMessage dispatch(Session session, String type, Object payload) {
        switch (type) {
            case "ping":
                return handlePing(session, payload);
            case "subscribe":
                return handleSubscribe(session, payload);
            case "unsubscribe":
                return handleUnsubscribe(session, payload);
            case "broadcast":
                return handleBroadcast(session, payload);
            default:
                return handleDefault(session, type, payload);
        }
    }

    /**
     * 处理 ping 消息，返回 pong（心跳检测）
     */
    private WebSocketMessage handlePing(Session session, Object payload) {
        return new WebSocketMessage("pong", Map.of(
                "sessionId", session.getId(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 处理订阅消息
     */
    private WebSocketMessage handleSubscribe(Session session, Object payload) {
        LOG.infof("Session %s subscribe: %s", session.getId(), payload);
        return new WebSocketMessage("subscribed", Map.of(
                "sessionId", session.getId(),
                "payload", payload != null ? payload : Map.of()
        ));
    }

    /**
     * 处理取消订阅消息
     */
    private WebSocketMessage handleUnsubscribe(Session session, Object payload) {
        LOG.infof("Session %s unsubscribe: %s", session.getId(), payload);
        return new WebSocketMessage("unsubscribed", Map.of(
                "sessionId", session.getId()
        ));
    }

    /**
     * 处理广播消息：将消息推送给所有连接的客户端
     */
    private WebSocketMessage handleBroadcast(Session session, Object payload) {
        LOG.infof("Session %s broadcast: %s", session.getId(), payload);
        WebSocketMessage broadcastMsg = new WebSocketMessage("broadcast", Map.of(
                "from", session.getId(),
                "data", payload != null ? payload : Map.of()
        ));
        sessionManager.broadcast(broadcastMsg);
        // 不单独回复发送者
        return null;
    }

    /**
     * 默认处理：回显消息（用于调试和未知类型兜底）
     */
    private WebSocketMessage handleDefault(Session session, String type, Object payload) {
        LOG.infof("Session %s unhandled type '%s', echoing back", session.getId(), type);
        Map<String, Object> echoPayload = new HashMap<>();
        echoPayload.put("originalType", type);
        echoPayload.put("originalPayload", payload != null ? payload : Map.of());
        return new WebSocketMessage("echo", echoPayload);
    }
}
