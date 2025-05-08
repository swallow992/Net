package com.example.chat.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 添加Message类导入
import com.example.chat.common.Message;

// 导入 RoomListPanel 类
import com.example.chat.client.RoomListPanel;
// 导入 ChatClient 类
import com.example.chat.client.ChatClient;

/**
 * 聊天室管理类，负责管理聊天室的创建、加入、切换等操作
 */
public class RoomManager {
    private ChatClient client;
    private JComboBox<String> roomComboBox;
    private String currentRoom;
    private RoomChangeListener roomChangeListener;

    // 存储聊天室密码
    private Map<String, String> roomPasswords = new HashMap<>();

    // 存储聊天室类型
    private Map<String, RoomListPanel.RoomType> roomTypes = new HashMap<>();

    // 存储聊天室未读消息数
    private Map<String, Integer> unreadMessages = new HashMap<>();

    // 存储所有可用的聊天室
    private List<String> availableRooms = new ArrayList<>();

    // 搜索关键词
    private String searchKeyword = "";

    // 筛选类型
    private RoomListPanel.RoomType filterType = null;

    public interface RoomChangeListener {
        void onRoomChanged(String newRoom);
    }

    public RoomManager(ChatClient client, JComboBox<String> roomComboBox) {
        this.client = client;
        this.roomComboBox = roomComboBox;

        // 添加默认聊天室
        availableRooms.add("大厅");
        currentRoom = "大厅";

        // 添加聊天室切换监听器
        roomComboBox.addActionListener(e -> {
            String selectedRoom = (String) roomComboBox.getSelectedItem();
            if (selectedRoom != null && !selectedRoom.equals(currentRoom)) {
                switchRoom(selectedRoom);
            }
        });

        // 记录默认聊天室类型
        roomTypes.put("大厅", RoomListPanel.RoomType.PUBLIC);

        // 初始化默认聊天室未读消息计数
        unreadMessages.clear(); // 修复：清空原有错误数据
        unreadMessages.put("大厅", 0);

        // 更新聊天室显示
        updateRoomDisplay();
    }

    public void setRoomChangeListener(RoomChangeListener listener) {
        this.roomChangeListener = listener;
    }

    /**
     * 检查聊天室是否存在
     */
    public boolean containsRoom(String roomName) {
        return availableRooms.contains(roomName);
    }

    /**
     * 增加聊天室未读消息计数
     */
    public void incrementUnreadCount(String roomName) {
        if (!roomName.equals(currentRoom)) {
            int count = unreadMessages.getOrDefault(roomName, 0);
            unreadMessages.put(roomName, count + 1);
            updateRoomDisplay();
        }
    }

    /**
     * 重置聊天室未读消息计数
     */
    public void resetUnreadCount(String roomName) {
        unreadMessages.put(roomName, 0);
        updateRoomDisplay();
    }

    /**
     * 获取聊天室未读消息数
     */
    public int getUnreadCount(String roomName) {
        return unreadMessages.getOrDefault(roomName, 0);
    }

    /**
     * 获取聊天室类型
     */
    public RoomListPanel.RoomType getRoomType(String roomName) {
        return roomTypes.getOrDefault(roomName, RoomListPanel.RoomType.PUBLIC);
    }

    /**
     * 更新聊天室显示（包括未读消息计数）
     */
    private void updateRoomDisplay() {
        // 保存当前选中的聊天室
        currentRoom = getCurrentRoom(); // 新增：确保当前房间状态同步
        String selectedRoom = (String) roomComboBox.getSelectedItem();

        // 临时禁用ActionListener，避免触发切换事件
        ActionListener[] listeners = roomComboBox.getActionListeners();
        for (ActionListener listener : listeners) {
            roomComboBox.removeActionListener(listener);
        }

        // 清空并重新添加所有聊天室
        roomComboBox.removeAllItems();

        // 获取筛选后的聊天室列表
        List<String> filteredRooms = getFilteredRooms();

        // 添加所有聊天室，带有未读消息计数
        for (String roomName : filteredRooms) {
            int unread = unreadMessages.getOrDefault(roomName, 0);

            // 如果有未读消息且不是当前聊天室，显示未读消息数
            if (unread > 0 && !roomName.equals(currentRoom)) {
                roomComboBox.addItem(roomName + " (" + unread + ")");
            } else {
                roomComboBox.addItem(roomName);
            }
        }

        // 恢复选中状态
        if (selectedRoom != null) {
            // 如果选中的房间名包含未读消息计数，去除它
            if (selectedRoom.contains(" (")) {
                selectedRoom = selectedRoom.substring(0, selectedRoom.indexOf(" ("));
            }

            // 查找匹配的房间名（可能带有未读消息计数）
            for (int i = 0; i < roomComboBox.getItemCount(); i++) {
                String item = roomComboBox.getItemAt(i);
                String itemName = item;
                if (item.contains(" (")) {
                    itemName = item.substring(0, item.indexOf(" ("));
                }

                if (itemName.equals(selectedRoom)) {
                    roomComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }

        // 重新添加ActionListener
        for (ActionListener listener : listeners) {
            roomComboBox.addActionListener(listener);
        }
    }

    /**
     * 获取筛选后的聊天室列表
     */
    private List<String> getFilteredRooms() {
        if (searchKeyword.isEmpty() && filterType == null) {
            return new ArrayList<>(availableRooms)
                    .stream().sorted().collect(Collectors.toList()); // 新增：排序优化显示
        }

        return availableRooms.stream()
            .filter(room -> {
                boolean matchesSearch = searchKeyword.isEmpty() || 
                    room.toLowerCase().contains(searchKeyword.toLowerCase());
                boolean matchesType = filterType == null || 
                    getRoomType(room) == filterType;
                return matchesSearch && matchesType;
            })
            .collect(Collectors.toList());
    }

    /**
     * 设置搜索关键词
     */
    public void setSearchKeyword(String keyword) {
        this.searchKeyword = keyword;
        updateRoomDisplay();
    }

    /**
     * 设置筛选类型
     */
    public void setFilterType(RoomListPanel.RoomType type) {
        this.filterType = type;
        updateRoomDisplay();
    }

    /**
     * 切换聊天室
     */
    public void switchRoom(String room) {
        if (client.isConnected() && !room.equals(currentRoom)) {
            try {
                // 检查是否需要密码
                if (roomTypes.getOrDefault(room, RoomListPanel.RoomType.PUBLIC) == RoomListPanel.RoomType.PASSWORD) {
                    // 使用 replaceAll 方法进行正则表达式替换
                    room = room.replaceAll(" \\(\\d+\\)", ""); 
                    String password = roomPasswords.get(room);
                    if (password == null) {
                        password = JOptionPane.showInputDialog(null, "请输入聊天室密码:", "密码保护的聊天室", JOptionPane.QUESTION_MESSAGE);
                        if (password == null || password.isEmpty()) {
                            return; // 用户取消输入密码
                        }
                        roomPasswords.put(room, password);
                    }

                    // 发送带密码的切换房间消息
                    Message message = new Message(Message.TYPE_SWITCH_ROOM);
                    message.put("room", room);
                    message.put("password", password);
                    client.sendMessage(message.toJson());
                } else {
                    // 发送普通切换房间消息
                    Message message = Message.createSwitchRoomMessage(room);
                    client.sendMessage(message.toJson());
                }

                currentRoom = room;

                // 通知监听器
                if (roomChangeListener != null) {
                    roomChangeListener.onRoomChanged(room);
                    resetUnreadCount(room); // 新增：切换时重置未读计数
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "切换聊天室失败: " + e.getMessage());
            }
        }
    }

    /**
     * 创建新聊天室
     */
    public void createRoom(String roomName, RoomListPanel.RoomType roomType, String password) {
        try {
            // 创建聊天室消息
            Message createRoomMessage = Message.createCreateRoomMessage(
                    roomName, 
                    roomType.toString().toLowerCase(), 
                    password
            );
            client.sendMessage(createRoomMessage.toJson());

            // 添加到可用聊天室列表
            availableRooms.removeIf(n -> n.equals(roomName)); // 修复：避免重复添加
            availableRooms.add(roomName);
            roomTypes.put(roomName, roomType);
            unreadMessages.put(roomName, 0);

            if (password != null && !password.isEmpty()) {
                roomPasswords.put(roomName, password);
            }

            // 更新聊天室显示
            updateRoomDisplay();

            // 自动切换到新房间
            switchRoom(roomName);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "创建聊天室失败: " + e.getMessage());
        }
    }

    /**
     * 创建新聊天室（简化版，用于菜单调用）
     */
    public void createNewRoom() {
        String roomName = JOptionPane.showInputDialog(null, "请输入聊天室名称:", "创建新聊天室", JOptionPane.QUESTION_MESSAGE);
        if (roomName == null || roomName.isEmpty()) {
            return;
        }

        // 选择聊天室类型
        Object[] options = {"公开", "私密", "密码保护"};
        int typeChoice = JOptionPane.showOptionDialog(null, 
            "请选择聊天室类型:", 
            "创建新聊天室", 
            JOptionPane.DEFAULT_OPTION, 
            JOptionPane.QUESTION_MESSAGE, 
            null, 
            options, 
            options[0]);

        RoomListPanel.RoomType roomType;
        String password = null;

        switch (typeChoice) {
            case 1: // 私密
                roomType = RoomListPanel.RoomType.PRIVATE;
                break;
            case 2: // 密码保护
                roomType = RoomListPanel.RoomType.PASSWORD;
                password = JOptionPane.showInputDialog(null, "请设置聊天室密码:", "创建密码保护的聊天室", JOptionPane.QUESTION_MESSAGE);
                if (password == null || password.isEmpty()) {
                    return; // 用户取消设置密码
                }
                break;
            case 0: // 公开
            default:
                roomType = RoomListPanel.RoomType.PUBLIC;
                break;
        }

        createRoom(roomName, roomType, password);
    }

    /**
     * 加入聊天室
     */
    public void joinRoom(String roomName, String password) {
        if (client.isConnected()) {
            try {
                // 创建加入聊天室消息
                Message message;
                if (password != null && !password.isEmpty()) {
                    message = new Message(Message.TYPE_JOIN_ROOM);
                    message.put("room", roomName);
                    message.put("password", password);
                } else {
                    message = Message.createJoinRoomMessage(roomName);
                }
                
                client.sendMessage(message.toJson());
                
                // 添加到可用聊天室列表（如果不存在）
                if (!availableRooms.contains(roomName)) {
                    availableRooms.add(roomName);
                    
                    // 如果是密码保护的聊天室，保存密码
                    if (password != null && !password.isEmpty()) {
                        roomPasswords.put(roomName, password);
                        roomTypes.put(roomName, RoomListPanel.RoomType.PASSWORD);
                    }
                    
                    // 初始化未读消息计数
                    unreadMessages.put(roomName, 0);
                    
                    // 更新聊天室显示
                    updateRoomDisplay();
                }
                
                // 切换到该聊天室
                switchRoom(roomName);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "加入聊天室失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取可加入的聊天室列表
     */
    private List<String> getAvailableRoomsToJoin() {
        // 这里应该从服务器获取可用聊天室列表
        // 简化实现，返回一些示例聊天室
        List<String> rooms = new ArrayList<>();
        rooms.add("技术交流");
        rooms.add("休闲聊天");
        rooms.add("游戏讨论");

        // 过滤掉已加入的聊天室
        return rooms.stream()
            .filter(room -> !availableRooms.contains(room))
            .collect(Collectors.toList());
    }

    /**
     * 退出聊天室
     */
    public void leaveRoom(String roomName) {
        if (roomName.equals("大厅")) {
            JOptionPane.showMessageDialog(null, "无法退出默认聊天室");
            return;
        }

        if (availableRooms.contains(roomName)) {
            try {
                // 发送退出聊天室消息
                Message leaveMessage = new Message(Message.TYPE_LEAVE_ROOM);
                leaveMessage.put("room", roomName);
                client.sendMessage(leaveMessage.toJson());

                // 如果当前在该聊天室，切换到大厅
                if (currentRoom.equals(roomName)) {
                    switchRoom("大厅");
                }

                // 从列表中移除
                availableRooms.remove(roomName);
                roomTypes.remove(roomName);
                unreadMessages.remove(roomName);
                roomPasswords.remove(roomName);

                // 更新显示
                updateRoomDisplay();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "退出聊天室失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取当前聊天室
     */
    public String getCurrentRoom() {
        return currentRoom;
    }

    /**
     * 获取所有可用聊天室
     */
    public List<String> getAvailableRooms() {
        return new ArrayList<>(availableRooms);
    }

    /**
     * 添加聊天室到列表（用于服务器推送的聊天室列表）
     */
    public void addRoom(String roomName, RoomListPanel.RoomType type) {
        if (!availableRooms.contains(roomName)) {
            availableRooms.add(roomName);
            roomTypes.put(roomName, type);
            unreadMessages.put(roomName, 0);
            updateRoomDisplay();
        }
    }
}