package org.acme.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * WebSocket 消息体
 * <p>
 * 报文格式:
 * <pre>
 * {
 *   "type": "消息类型",
 *   "payload": { ... }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息负载（任意结构）
     */
    private Object payload;

    public WebSocketMessage() {
    }

    public WebSocketMessage(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "WebSocketMessage{type='" + type + "', payload=" + payload + "}";
    }
}
