package com.example.chat.client;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户状态管理类，用于管理和显示用户状态
 */
public class UserStatusManager {
    // 用户状态枚举
    public enum UserStatus {
        ONLINE("在线", new Color(0, 153, 0)),
        AWAY("离开", Color.ORANGE),
        BUSY("忙碌", Color.RED),
        OFFLINE("离线", Color.GRAY);
        
        private final String displayName;
        private final Color color;
        
        UserStatus(String displayName, Color color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public Color getColor() {
            return color;
        }
    }
    
    // 存储用户状态
    private final Map<String, UserStatus> userStatuses;
    
    // 当前用户名
    private final String currentUsername;
    
    // 当前用户状态
    private UserStatus currentStatus;
    
    // 用户列表引用（新增）
    private JList<String> userList;
    
    public UserStatusManager(String username) {
        this.userStatuses = new HashMap<>();
        this.currentUsername = username;
        this.currentStatus = UserStatus.ONLINE;
    }
    
    // 设置用户列表引用（新增）
    public void setUserList(JList<String> userList) {
        this.userList = userList;
    }
    
    // 设置用户状态
    public void setUserStatus(String username, UserStatus status) {
        userStatuses.put(username, status);
        if (userList != null) {
            userList.repaint(); // 自动刷新用户列表
        }
    }
    
    // 获取用户状态
    public UserStatus getUserStatus(String username) {
        return userStatuses.getOrDefault(username, UserStatus.ONLINE);
    }
    
    // 设置当前用户状态
    public void setCurrentStatus(UserStatus status) {
        this.currentStatus = status;
        if (userList != null) {
            userList.repaint(); // 自动刷新用户列表
        }
    }
    
    // 获取当前用户状态
    public UserStatus getCurrentStatus() {
        return currentStatus;
    }
    
    // 创建状态选择菜单
    public JMenu createStatusMenu() {
        JMenu statusMenu = new JMenu("状态");
        
        for (UserStatus status : UserStatus.values()) {
            JMenuItem item = new JMenuItem(status.getDisplayName());
            item.setForeground(status.getColor());
            item.addActionListener(e -> setCurrentStatus(status));
            statusMenu.add(item);
        }
        
        return statusMenu;
    }
    
    // 自定义用户列表单元格渲染器
    public ListCellRenderer<?> createUserCellRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                
                if (value == null) {
                    return label;
                }
                
                String username = value.toString();
                
                // 如果是当前用户，显示状态
                if (username.contains(currentUsername)) {
                    label.setForeground(currentStatus.getColor());
                    label.setText(username + " - " + currentStatus.getDisplayName());
                } else {
                    // 处理其他用户的状态显示
                    String name = username;
                    if (username.contains(" ")) {
                        name = username.substring(0, username.indexOf(" "));
                    }
                    
                    UserStatus status = getUserStatus(name);
                    if (status != null) {
                        if (!isSelected) {
                            label.setForeground(status.getColor());
                        }
                        label.setText(username + " - " + status.getDisplayName());
                    }
                }
                
                return label;
            }
        };
    }
}