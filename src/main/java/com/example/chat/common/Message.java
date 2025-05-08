package com.example.chat.common;

import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.io.IOException;

/**
 * 消息协议类，用于客户端和服务端之间的通信
 * 使用JSON格式定义不同类型的消息
 */
public class Message {
    // 消息类型常量
    public static final String TYPE_LOGIN = "login";
    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_RICH_MESSAGE = "rich_message";
    public static final String TYPE_SWITCH_ROOM = "switch_room";
    public static final String TYPE_USER_LIST = "user_list";
    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_HEARTBEAT = "heartbeat";
    // 新增消息类型
    public static final String TYPE_CREATE_ROOM = "create_room";
    public static final String TYPE_JOIN_ROOM = "join_room";
    public static final String TYPE_LEAVE_ROOM = "leave_room";
    public static final String TYPE_ROOM_LIST = "room_list";
    public static final String TYPE_STATUS_UPDATE = "status_update";
    public static final String TYPE_NOTIFICATION = "notification";

    private String type;
    private Map<String, Object> data;

    public Message(String type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    public String getType() {
        return type;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 将消息转换为JSON字符串
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"")
                .append(type)
                .append("\",");

        int count = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (count > 0) {
                sb.append(",");
            }
            sb.append("\"")
                    .append(entry.getKey())
                    .append("\":\"")
                    .append(entry.getValue())
                    .append("\"");
            count++;
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 创建登录消息
     */
    public static Message createLoginMessage(String username, String password) {
        Message message = new Message(TYPE_LOGIN);
        message.put("username", username);
        message.put("password", password);
        return message;
    }

    /**
     * 创建聊天消息
     */
    public static Message createChatMessage(String room, String content) {
        Message message = new Message(TYPE_MESSAGE);
        message.put("room", room);
        message.put("content", content);
        return message;
    }

    /**
     * 创建切换房间消息
     */
    public static Message createSwitchRoomMessage(String room) {
        Message message = new Message(TYPE_SWITCH_ROOM);
        message.put("room", room);
        return message;
    }

    /**
     * 创建心跳消息
     */
    public static Message createHeartbeatMessage() {
        return new Message(TYPE_HEARTBEAT);
    }

    /**
     * 创建注册消息
     */
    public static Message createRegisterMessage(String username, String password) {
        Message message = new Message(TYPE_REGISTER);
        message.put("username", username);
        message.put("password", password);
        return message;
    }

    /**
     * 创建富文本消息（支持表情和格式化文本）
     */
    public static Message createRichMessage(String room, String content, boolean hasEmoji) {
        Message message = new Message(TYPE_RICH_MESSAGE);
        message.put("room", room);
        message.put("content", content);
        message.put("hasEmoji", String.valueOf(hasEmoji));
        return message;
    }

    /**
     * 创建创建聊天室消息
     */
    public static Message createCreateRoomMessage(String roomName, String roomType, String password) {
        Message message = new Message(TYPE_CREATE_ROOM);
        message.put("room", roomName);
        message.put("type", roomType);
        if (password != null && !password.isEmpty()) {
            message.put("password", password);
        }
        return message;
    }

    /**
     * 创建加入聊天室消息
     */
    public static Message createJoinRoomMessage(String roomName) {
        Message message = new Message(TYPE_JOIN_ROOM);
        message.put("room", roomName);
        return message;
    }

    /**
     * 创建离开聊天室消息
     */
    public static Message createLeaveRoomMessage(String roomName) {
        Message message = new Message(TYPE_LEAVE_ROOM);
        message.put("room", roomName);
        return message;
    }

    /**
     * 创建聊天室列表消息
     */
    public static Message createRoomListMessage(String rooms) {
        Message message = new Message(TYPE_ROOM_LIST);
        message.put("rooms", rooms);
        return message;
    }

    /**
     * 创建状态更新消息
     */
    public static Message createStatusUpdateMessage(String status) {
        Message message = new Message(TYPE_STATUS_UPDATE);
        message.put("status", status);
        return message;
    }

    /**
     * 创建通知消息
     */
    public static Message createNotificationMessage(String title, String content, String level) {
        Message message = new Message(TYPE_NOTIFICATION);
        message.put("title", title);
        message.put("content", content);
        message.put("level", level); // info, warning, error
        return message;
    }

    /**
     * 将消息发送到指定的 SocketChannel
     * @param channel SocketChannel
     * @throws IOException
     */
    public void send(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(this.toJson().getBytes());
        channel.write(buffer);
    }

    /**
     * 创建系统消息
     */
    public static Message createSystemMessage(String content) {
        Message message = new Message(Message.TYPE_SYSTEM);
        message.put("content", content);
        return message;
    }
}

