package com.example.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.ListSelectionListener;

import com.example.chat.common.Message;

/**
 * 聊天室列表面板，支持搜索和筛选功能
 */
public class RoomListPanel extends JPanel {
    private ChatClient client;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;
    private JTextField searchField;
    private JComboBox<String> filterComboBox;
    private Map<String, Integer> unreadMessages; // 存储每个聊天室的未读消息数
    private Map<String, RoomType> roomTypes; // 存储聊天室类型（公开、私密、密码）
    private String currentRoom;
    private RoomChangeListener roomChangeListener;

    public enum RoomType {
        PUBLIC, PRIVATE, PASSWORD
    }

    public interface RoomChangeListener {
        void onRoomChanged(String newRoom);
    }

    public RoomListPanel(ChatClient client) {
        this.client = client;
        this.unreadMessages = new HashMap<>();
        this.roomTypes = new HashMap<>();
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        // 搜索和筛选面板
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchField = new JTextField();
        searchField.setToolTipText("搜索聊天室");
        searchField.addActionListener(e -> filterRooms());

        String[] filterOptions = {"全部", "公开", "私密", "密码保护"};
        filterComboBox = new JComboBox<>(filterOptions);
        filterComboBox.addActionListener(e -> filterRooms());

        JButton searchButton = new JButton("搜索");
        searchButton.addActionListener(e -> filterRooms());

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(filterComboBox, BorderLayout.EAST);
        searchPanel.add(searchButton, BorderLayout.SOUTH);

        // 聊天室列表
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setCellRenderer(new RoomCellRenderer());
        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = roomList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String roomName = getRoomNameFromDisplay(roomListModel.getElementAt(index));
                        switchRoom(roomName);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setPreferredSize(new Dimension(200, 300));

        // 按钮面板
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton createButton = new JButton("创建聊天室");
        createButton.addActionListener(e -> createNewRoom());
        JButton joinButton = new JButton("加入聊天室");
        joinButton.addActionListener(e -> joinRoom());

        buttonPanel.add(createButton);
        buttonPanel.add(joinButton);

        // 添加组件到面板
        add(searchPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // 初始化默认聊天室
        addRoom("大厅", RoomType.PUBLIC);
        currentRoom = "大厅";
    }

    // 自定义单元格渲染器，显示未读消息数
    private class RoomCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            String roomName = getRoomNameFromDisplay((String) value);

            // 根据聊天室类型设置不同的显示效果，不使用图标
            if (roomTypes.containsKey(roomName)) {
                RoomType type = roomTypes.get(roomName);
                if (type == RoomType.PUBLIC) {
                    label.setText("🌐 " + label.getText());
                } else if (type == RoomType.PRIVATE) {
                    label.setText("🔒 " + label.getText());
                } else if (type == RoomType.PASSWORD) {
                    label.setText("🔑 " + label.getText());
                }
            }

            return label;
        }
    }

    // 从显示文本中提取聊天室名称（去除未读消息计数）
    private String getRoomNameFromDisplay(String displayText) {
        if (displayText.contains(" (")) {
            return displayText.substring(0, displayText.indexOf(" ("));
        }
        return displayText;
    }

    // 添加聊天室到列表
    public void addRoom(String roomName, RoomType type) {
        if (!roomListModel.contains(roomName)) {
            roomListModel.addElement(roomName);
            roomTypes.put(roomName, type);
            unreadMessages.put(roomName, 0);
        }
    }

    // 切换聊天室
    public void switchRoom(String roomName) {
        if (roomName != null && !roomName.equals(currentRoom)) {
            try {
                // 发送切换聊天室消息
                client.sendMessage(Message.createSwitchRoomMessage(roomName).toJson());

                // 重置未读消息计数
                unreadMessages.put(roomName, 0);
                updateRoomDisplay();

                // 更新当前聊天室
                String oldRoom = currentRoom;
                currentRoom = roomName;

                // 通知监听器
                if (roomChangeListener != null) {
                    roomChangeListener.onRoomChanged(roomName);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "切换聊天室失败: " + e.getMessage());
            }
        }
    }

    /**
     * 设置当前聊天室（用于与RoomManager同步）
     */
    public void setCurrentRoom(String roomName) {
        if (roomName != null && !roomName.equals(currentRoom)) {
            // 重置未读消息计数
            unreadMessages.put(roomName, 0);
            updateRoomDisplay();

            // 更新当前聊天室
            currentRoom = roomName;

            // 高亮显示当前聊天室
            for (int i = 0; i < roomListModel.size(); i++) {
                String item = roomListModel.getElementAt(i);
                String itemName = getRoomNameFromDisplay(item);
                if (itemName.equals(roomName)) {
                    roomList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    // 创建新聊天室
    private void createNewRoom() {
        JTextField roomNameField = new JTextField();
        String[] roomTypes = {"公开", "私密", "密码保护"};
        JComboBox<String> typeComboBox = new JComboBox<>(roomTypes);
        JPasswordField passwordField = new JPasswordField();
        passwordField.setEnabled(false);

        // 当选择密码保护时启用密码输入框
        typeComboBox.addActionListener(e -> {
            passwordField.setEnabled("密码保护".equals(typeComboBox.getSelectedItem()));
        });

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("聊天室名称:"));
        panel.add(roomNameField);
        panel.add(new JLabel("聊天室类型:"));
        panel.add(typeComboBox);
        panel.add(new JLabel("密码 (如果需要):"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(this, panel, "创建新聊天室",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String roomName = roomNameField.getText().trim();
            String type = (String) typeComboBox.getSelectedItem();
            String password = new String(passwordField.getPassword());

            if (roomName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "聊天室名称不能为空");
                return;
            }

            try {
                RoomType roomType = RoomType.PUBLIC;
                if ("私密".equals(type)) {
                    roomType = RoomType.PRIVATE;
                } else if ("密码保护".equals(type)) {
                    roomType = RoomType.PASSWORD;
                    if (password.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "密码保护的聊天室必须设置密码");
                        return;
                    }
                }

                // 发送创建聊天室消息
                Message createRoomMsg = Message.createCreateRoomMessage(roomName, roomType.toString().toLowerCase(), password);
                client.sendMessage(createRoomMsg.toJson());

                // 添加到本地列表
                addRoom(roomName, roomType);

                // 切换到新创建的聊天室
                switchRoom(roomName);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "创建聊天室失败: " + e.getMessage());
            }
        }
    }

    // 加入聊天室
    private void joinRoom() {
        String roomName = JOptionPane.showInputDialog(this, "请输入要加入的聊天室名称:");
        if (roomName != null && !roomName.trim().isEmpty()) {
            try {
                // 发送加入聊天室消息
                client.sendMessage(Message.createJoinRoomMessage(roomName.trim()).toJson());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "加入聊天室失败: " + e.getMessage());
            }
        }
    }

    // 过滤聊天室列表
    private void filterRooms() {
        String searchText = searchField.getText().toLowerCase();
        String filterType = (String) filterComboBox.getSelectedItem();

        // 保存当前选中的聊天室
        String selectedRoom = roomList.getSelectedValue();

        // 清空并重新添加符合条件的聊天室
        roomListModel.clear();

        for (String roomName : roomTypes.keySet()) {
            RoomType type = roomTypes.get(roomName);
            boolean typeMatch = "全部".equals(filterType) ||
                    ("公开".equals(filterType) && type == RoomType.PUBLIC) ||
                    ("私密".equals(filterType) && type == RoomType.PRIVATE) ||
                    ("密码保护".equals(filterType) && type == RoomType.PASSWORD);

            if (roomName.toLowerCase().contains(searchText) && typeMatch) {
                // 添加带有未读消息计数的显示
                int unread = unreadMessages.getOrDefault(roomName, 0);
                if (unread > 0 && !roomName.equals(currentRoom)) {
                    roomListModel.addElement(roomName + " (" + unread + ")");
                } else {
                    roomListModel.addElement(roomName);
                }
            }
        }

        // 恢复选中状态
        if (selectedRoom != null) {
            roomList.setSelectedValue(selectedRoom, true);
        }
    }

    // 更新聊天室显示（包括未读消息计数）
    public void updateRoomDisplay() {
        // 保存当前选中的聊天室
        String selectedRoom = roomList.getSelectedValue();

        // 清空并重新添加所有聊天室
        roomListModel.clear();

        for (String roomName : roomTypes.keySet()) {
            int unread = unreadMessages.getOrDefault(roomName, 0);
            if (unread > 0 && !roomName.equals(currentRoom)) {
                roomListModel.addElement(roomName + " (" + unread + ")");
            } else {
                roomListModel.addElement(roomName);
            }
        }

        // 恢复选中状态
        if (selectedRoom != null) {
            roomList.setSelectedValue(selectedRoom, true);
        }
    }

    // 增加聊天室未读消息计数
    public void incrementUnreadCount(String roomName) {
        if (!roomName.equals(currentRoom)) {
            int count = unreadMessages.getOrDefault(roomName, 0);
            unreadMessages.put(roomName, count + 1);
            updateRoomDisplay();
        }
    }

    // 获取当前聊天室
    public String getCurrentRoom() {
        return currentRoom;
    }

    // 设置聊天室变更监听器
    public void setRoomChangeListener(RoomChangeListener listener) {
        this.roomChangeListener = listener;
    }
}