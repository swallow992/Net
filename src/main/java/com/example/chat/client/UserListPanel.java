package com.example.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.example.chat.common.Message;

/**
 * 在线用户列表面板，支持显示用户状态
 */
public class UserListPanel extends JPanel {
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private Map<String, UserStatus> userStatusMap = new HashMap<>();
    private String currentUsername;
    private ChatClient client;
    
    public enum UserStatus {
        ONLINE("在线", new Color(0, 153, 0)),
        AWAY("离开", new Color(255, 153, 0)),
        BUSY("忙碌", new Color(204, 0, 0)),
        OFFLINE("离线", Color.GRAY);
        
        private String displayName;
        private Color color;
        
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
    
    public UserListPanel(String currentUsername, ChatClient client) {
        this.currentUsername = currentUsername;
        this.client = client;
        
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("在线用户"));
        
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserCellRenderer());
        
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        add(scrollPane, BorderLayout.CENTER);
        
        // 添加状态切换面板
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JLabel statusLabel = new JLabel("我的状态:");
        String[] statuses = {"在线", "离开", "忙碌"};
        JComboBox<String> statusCombo = new JComboBox<>(statuses);
        
        statusPanel.add(statusLabel);
        statusPanel.add(statusCombo);
        
        add(statusPanel, BorderLayout.SOUTH);
        
        // 状态变更监听
        statusCombo.addActionListener(e -> {
            String selectedStatus = (String) statusCombo.getSelectedItem();
            UserStatus status = UserStatus.ONLINE;
            
            if ("离开".equals(selectedStatus)) {
                status = UserStatus.AWAY;
            } else if ("忙碌".equals(selectedStatus)) {
                status = UserStatus.BUSY;
            }
            
            setUserStatus(currentUsername, status);
            // 发送状态变更消息到服务器
            try {
                // 创建状态更新消息
                Message statusMessage = new Message(Message.TYPE_STATUS_UPDATE);
                statusMessage.put("username", currentUsername);
                statusMessage.put("status", status.name().toLowerCase());
                
                // 如果有ChatClient实例，发送消息
                if (client != null) {
                    client.sendMessage(statusMessage.toJson());
                }
            } catch (Exception ex) {
                System.err.println("发送状态更新失败: " + ex.getMessage());
            }
        });
        
        // 默认将当前用户设置为在线状态
        setUserStatus(currentUsername, UserStatus.ONLINE);
    }
    
    // 更新用户列表
    public void updateUserList(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            
            // 始终确保当前用户显示在列表顶部
            if (currentUsername != null && !currentUsername.isEmpty()) {
                userListModel.addElement(currentUsername + " (我)");
            }
            
            // 添加其他用户
            if (users != null) {
                for (String user : users) {
                    // 跳过当前用户（避免重复）和空用户名
                    if (!user.equals(currentUsername) && !user.trim().isEmpty()) {
                        userListModel.addElement(user);
                        
                        // 如果是新用户，默认设置为在线状态
                        if (!userStatusMap.containsKey(user)) {
                            userStatusMap.put(user, UserStatus.ONLINE);
                        }
                    }
                }
            }
        });
    }
    
    // 设置用户状态
    public void setUserStatus(String username, UserStatus status) {
        userStatusMap.put(username, status);
        // 刷新列表显示
        userList.repaint();
    }
    
    // 获取用户状态
    public UserStatus getUserStatus(String username) {
        return userStatusMap.getOrDefault(username, UserStatus.OFFLINE);
    }
    
    // 自定义单元格渲染器，用于显示用户状态
    private class UserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                int index, boolean isSelected, boolean cellHasFocus) {
            
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            
            String username = value.toString();
            boolean isCurrentUser = username.endsWith(" (我)");
            
            if (isCurrentUser) {
                username = username.substring(0, username.length() - 4);
            }
            
            UserStatus status = getUserStatus(username);
            
            // 设置状态图标和颜色
            label.setIcon(createStatusIcon(status.getColor()));
            
            // 设置带状态的文本
            if (isCurrentUser) {
                label.setText(username + " (我) - " + status.getDisplayName());
            } else {
                label.setText(username + " - " + status.getDisplayName());
            }
            
            return label;
        }
        
        // 创建状态图标
        private Icon createStatusIcon(Color color) {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(color);
                    g2d.fillOval(x, y + 2, 10, 10);
                    g2d.dispose();
                }
                
                @Override
                public int getIconWidth() {
                    return 14;
                }
                
                @Override
                public int getIconHeight() {
                    return 14;
                }
            };
        }
    }
}