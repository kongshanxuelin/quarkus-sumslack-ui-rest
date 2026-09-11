package org.acme.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;
import org.acme.dto.WebSocketMessage;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话管理器
 * <p>
 * 负责维护所有连接到 /nb-provider-websocket-gateway/gateway3 的客户端会话，
 * 提供单发、广播、消息序列化等能力。
 */
@ApplicationScoped
public class WebSocketSessionManager {

    private static final Logger LOG = Logger.getLogger(WebSocketSessionManager.class);

    /**
     * 所有活跃的 WebSocket 会话（sessionId -> Session）
     */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * 用于 JSON 序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注册一个新的会话
     */
    public void addSession(Session session) {
        sessions.put(session.getId(), session);
        LOG.infof("WebSocket session added: %s, total: %d", session.getId(), sessions.size());
    }

    /**
     * 移除一个会话
     */
    public void removeSession(Session session) {
        sessions.remove(session.getId());
        LOG.infof("WebSocket session removed: %s, total: %d", session.getId(), sessions.size());
    }

    /**
     * 获取当前活跃会话数
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 向指定会话发送消息
     *
     * @param sessionId 目标会话ID
     * @param message   消息体
     * @return true 表示发送成功，false 表示会话不存在或发送失败
     */
    public boolean sendToSession(String sessionId, WebSocketMessage message) {
        Session session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            LOG.warnf("Cannot send message, session not found or closed: %s", sessionId);
            return false;
        }
        return doSend(session, message);
    }

    /**
     * 向指定会话发送原始文本
     */
    public boolean sendTextToSession(String sessionId, String text) {
        Session session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            LOG.warnf("Cannot send text, session not found or closed: %s", sessionId);
            return false;
        }
        return doSendText(session, text);
    }

    /**
     * 广播消息给所有活跃会话
     */
    public void broadcast(WebSocketMessage message) {
        String json = serialize(message);
        if (json == null) {
            return;
        }
        for (Session session : sessions.values()) {
            if (session.isOpen()) {
                doSendText(session, json);
            }
        }
    }

    /**
     * 向所有活跃会话广播原始文本
     */
    public void broadcastText(String text) {
        for (Session session : sessions.values()) {
            if (session.isOpen()) {
                doSendText(session, text);
            }
        }
    }

    /**
     * 获取所有活跃的会话ID
     */
    public Set<String> getActiveSessionIds() {
        return new CopyOnWriteArraySet<>(sessions.keySet());
    }

    /**
     * 将消息对象序列化为 JSON 字符串
     */
    public String serialize(WebSocketMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize WebSocketMessage: %s", message);
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为消息对象
     */
    public WebSocketMessage deserialize(String json) {
        try {
            return objectMapper.readValue(json, WebSocketMessage.class);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deserialize WebSocket message: %s", json);
            return null;
        }
    }

    private boolean doSend(Session session, WebSocketMessage message) {
        String json = serialize(message);
        if (json == null) {
            return false;
        }
        return doSendText(session, json);
    }

    private boolean doSendText(Session session, String text) {
        try {
            // 必须使用 asyncRemote：Quarkus 的 WebSocket 回调在 Vert.x event loop（IO 线程）上执行，
            // basicRemote.sendText() 是阻塞调用，会抛出 IllegalStateException。
            // asyncRemote.sendText() 是非阻塞的，可安全在 IO 线程使用。
            session.getAsyncRemote().sendText(text);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send WebSocket message to session: %s", session.getId());
            return false;
        }
    }
}
