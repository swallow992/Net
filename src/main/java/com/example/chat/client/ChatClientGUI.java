package com.example.chat.client;

// 新增加密相关导入
import com.example.chat.client.util.CryptoUtils; // 自定义加密工具类
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.example.chat.common.Message;
import com.example.chat.common.MessageParser;

public class ChatClientGUI extends JFrame implements ChatClient.ConnectionListener, EmojiSelector.EmojiListener, ChatClient.MessageListener {
    private ChatClient client;
    private JTextPane messageArea;
    private JTextPane inputField;
    private EmojiSelector emojiSelector;
    private JToggleButton emojiButton;
    private JButton sendButton;
    private JComboBox<String> roomComboBox;
    private RoomManager roomManager;
    private JLabel statusLabel;
    private String username;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private NotificationManager notificationManager;
    private UserListPanel userListPanel;
    private RoomListPanel roomListPanel;
    private KeyPair dhKeyPair;           // DH密钥对
    private SecretKey sharedSecretKey;   // 共享密钥
    private byte[] iv;                   // 初始化向量
    private boolean isEncrypted = false; // 加密状态标识

    // 存储每个聊天室的消息历史
    private Map<String, StringBuilder> roomMessages = new HashMap<>();

    public ChatClientGUI(ChatClient client, String username) {
        this.client = client;
        this.username = username;
        setTitle("NIO 聊天客户端 - " + username);
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 设置连接状态监听器
        client.setConnectionListener(this);
        
        // 设置消息监听器
        client.setMessageListener(this);

        // 初始化消息历史记录
        roomMessages = new HashMap<>();

        // 初始化通知管理器
        notificationManager = new NotificationManager(this);

        initUI();
        startReceiveThread();

        // 窗口关闭时断开连接
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
            }
        });
    }

    private void initUI() {
        // 创建菜单栏
        JMenuBar menuBar = new JMenuBar();


        
        // 连接菜单
        JMenu connectionMenu = new JMenu("连接");
        JMenuItem reconnectItem = new JMenuItem("重新连接");
        reconnectItem.addActionListener(e -> {
            if (!client.isConnected()) {
                client.connect();
            }
        });
        connectionMenu.add(reconnectItem);

        // 设置菜单
        JMenu settingsMenu = new JMenu("设置");
        JMenuItem notificationItem = new JMenuItem("通知设置");
        notificationItem.addActionListener(e -> {
            showNotificationSettings();
        });
        settingsMenu.add(notificationItem);

        menuBar.add(connectionMenu);
        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);

        // 状态栏
        statusLabel = new JLabel("已连接到服务器");
        statusLabel.setForeground(Color.GREEN);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 0));

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 创建聊天室下拉框
        roomComboBox = new JComboBox<>();

        // 初始化聊天室管理器
        roomManager = new RoomManager(client, roomComboBox);

        // 左侧面板 - 聊天室列表
        roomListPanel = new RoomListPanel(client);
        roomManager.setRoomChangeListener(newRoom -> {
            displaySystemMessage("已切换到聊天室: " + newRoom);
            roomManager.resetUnreadCount(newRoom); // 新增：切换时清除未读
            roomListPanel.setCurrentRoom(newRoom);
            // 加载聊天室历史消息
            loadRoomMessages(newRoom);
        });

        // 右侧面板 - 用户列表
        userListPanel = new UserListPanel(username, client);

        // 中间面板 - 消息区域和输入区域
        JPanel centerPanel = new JPanel(new BorderLayout(0, 5));

        // 消息区
        messageArea = new JTextPane();
        messageArea.setEditable(false);
        messageArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        StyledDocument doc = messageArea.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_LEFT);
        doc.setParagraphAttributes(0, doc.getLength(), center, false);
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messageScrollPane.setPreferredSize(new Dimension(600, 400));

        // 输入区
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        inputField = new JTextPane();
        inputField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume(); // 阻止换行
                    sendMessage();
                }
            }
        });
        JScrollPane inputScrollPane = new JScrollPane(inputField);
        inputScrollPane.setPreferredSize(new Dimension(600, 100));

        // 表情选择器
        emojiSelector = new EmojiSelector();
        emojiSelector.setEmojiListener(this);
        emojiSelector.setVisible(false);

        JToggleButton encryptButton = new JToggleButton("🔒 加密");
        encryptButton.addActionListener(e -> {
            isEncrypted = encryptButton.isSelected();
            if (isEncrypted) {
                displaySystemMessage("已启用端到端加密");
            } else {
                displaySystemMessage("已关闭端到端加密");
            }
        });
        // 表情按钮
        emojiButton = new JToggleButton("😊");
        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        emojiButton.addActionListener(e -> {
            emojiSelector.setVisible(emojiButton.isSelected());
            validate();
            repaint();
        });

        sendButton = new JButton("发送");
        sendButton.addActionListener(e -> sendMessage());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(encryptButton); // 新增
        buttonPanel.add(emojiButton);
        buttonPanel.add(sendButton);

        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        // 添加组件到中间面板
        centerPanel.add(messageScrollPane, BorderLayout.CENTER);
        centerPanel.add(inputPanel, BorderLayout.SOUTH);

        // 顶部面板 - 包含聊天室选择器和状态栏
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel roomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        roomPanel.add(new JLabel("当前聊天室："));
        roomPanel.add(roomComboBox);
        topPanel.add(roomPanel, BorderLayout.WEST);
        topPanel.add(statusLabel, BorderLayout.EAST);

        // 创建左右分割面板
        JSplitPane leftRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                roomListPanel, new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, userListPanel));
        leftRightSplit.setDividerLocation(200); // 左侧宽度

        // 添加到主窗口
        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(leftRightSplit, BorderLayout.CENTER);
        getContentPane().add(emojiSelector, BorderLayout.SOUTH);

    }

    private void loadRoomMessages(String newRoom) {
        StringBuilder history = roomMessages.get(newRoom);
        try {
            StyledDocument doc = messageArea.getStyledDocument();
            doc.remove(0, doc.getLength()); // 清空当前显示
            
            if (history != null) {
                // 使用默认样式添加历史消息
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attr, "微软雅黑");
                StyleConstants.setFontSize(attr, 14);
                doc.insertString(doc.getLength(), history.toString(), attr);
            }
            
            messageArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || !client.isConnected()) return;

        try {
            Message message;

            // ===================== 新增代码：加密消息处理 =====================
            if (isEncrypted) {
                // 1. 使用共享密钥和IV加密消息
                String encryptedPayload = CryptoUtils.encrypt(text, sharedSecretKey, iv);

                // 2. 创建加密消息（目标用户写死为"Bob"，实际应通过UI选择）
                message = Message.createEncryptedMessage(
                        encryptedPayload,
                        Base64.getEncoder().encodeToString(iv), // 将IV转为Base64字符串
                        "Bob" // 目标用户
                );

                // 3. 生成新的IV（避免重复使用）
                iv = CryptoUtils.generateIV();
            }
            // ===================== 保留原有逻辑：普通消息 =====================
            else {
                // 检查是否包含表情
                boolean hasEmoji = false;
                for (String emoji : EmojiSelector.EMOJIS) {
                    if (text.contains(emoji)) {
                        hasEmoji = true;
                        break;
                    }
                }

                // 创建普通消息或富文本消息
                if (hasEmoji) {
                    message = Message.createRichMessage(
                            (String) roomComboBox.getSelectedItem(),
                            text,
                            true
                    );
                } else {
                    message = Message.createChatMessage(
                            (String) roomComboBox.getSelectedItem(),
                            text
                    );
                }
            }

            // ===================== 公共代码：发送消息 =====================
            client.sendMessage(message.toJson());
            inputField.setText("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "发送失败: " + e.getMessage());
        }
    }

    // 添加创建新聊天室的方法
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
            String typeStr = (String) typeComboBox.getSelectedItem();
            String password = new String(passwordField.getPassword());

            if (roomName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "聊天室名称不能为空");
                return;
            }

            // 转换类型
            RoomListPanel.RoomType type = RoomListPanel.RoomType.PUBLIC;
            if ("私密".equals(typeStr)) {
                type = RoomListPanel.RoomType.PRIVATE;
            } else if ("密码保护".equals(typeStr)) {
                type = RoomListPanel.RoomType.PASSWORD;
                if (password.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "密码保护的聊天室必须设置密码");
                    return;
                }
            }

            // 创建聊天室
            roomManager.createRoom(roomName, type, password);
        }
    }

    // 加入聊天室方法
    private void joinRoom() {
        String roomName = JOptionPane.showInputDialog(this, "请输入要加入的聊天室名称:");
        if (roomName != null && !roomName.trim().isEmpty()) {
            // 检查是否需要密码
            RoomListPanel.RoomType roomType = roomManager.getRoomType(roomName.trim());

            if (roomType == RoomListPanel.RoomType.PASSWORD) {
                String password = JOptionPane.showInputDialog(this, "请输入聊天室密码:");
                if (password != null) {
                    roomManager.joinRoom(roomName.trim(), password);
                }
            } else {
                roomManager.joinRoom(roomName.trim(), null);
            }
        }
    }

    private void showNotificationSettings() {
        JCheckBox soundCheckBox = new JCheckBox("启用声音提醒", true);
        JCheckBox popupCheckBox = new JCheckBox("启用弹窗提醒", true);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(soundCheckBox);
        panel.add(popupCheckBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "通知设置",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            notificationManager.setSoundEnabled(soundCheckBox.isSelected());
            notificationManager.setPopupEnabled(popupCheckBox.isSelected());
        }
    }

    private void displayRichMessage(String text, Color color, boolean hasEmoji) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timeFormat.format(new Date());
            String formattedMessage = timestamp + " " + text + "\n";
            StyledDocument doc = messageArea.getStyledDocument();

            // 创建样式
            javax.swing.text.Style style = messageArea.addStyle("MessageStyle", null);
            StyleConstants.setForeground(style, color);
            StyleConstants.setFontFamily(style, hasEmoji ? "Segoe UI Emoji" : "微软雅黑");
            StyleConstants.setFontSize(style, 14);

            try {
                doc.insertString(doc.getLength(), formattedMessage, style);

                // 保存消息到当前聊天室历史记录
                String currentRoom = roomManager.getCurrentRoom();
                if (currentRoom != null) {
                    StringBuilder history = roomMessages.get(currentRoom);
                    if (history == null) {
                        history = new StringBuilder();
                        roomMessages.put(currentRoom, history);
                    }
                    history.append(formattedMessage);
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

            // 滚动到最新消息
            messageArea.setCaretPosition(doc.getLength());
        });
    }

    private void displayMessage(String text, Color color) {
        displayRichMessage(text, color, false);
    }

    private void displaySystemMessage(String text) {
        displayMessage("[系统] " + text, Color.GRAY);
    }

    private void startReceiveThread() {
        new Thread(() -> {
            while (client.isConnected()) {
                try {
                    String json = client.receiveMessage();
                    if (json != null) {
                        // 使用消息解析器解析消息
                        Message message = MessageParser.parseMessage(json);
                        if (message != null) {
                            processMessage(message);
                        }
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    // 忽略异常，连接状态由ConnectionListener处理
                }
            }
        }).start();
    }

    private void processMessage(Message message) {
        String type = message.getType();
    
        switch (type) {
            case Message.TYPE_MESSAGE: {
                String roomName = (String) message.get("room");
                String content = (String) message.get("content");
                String sender = (String) message.get("sender");
    
                // 显示消息
                displayMessage(content, Color.BLACK);
    
                // 如果消息不是当前聊天室的，增加未读消息计数并发送通知
                if (roomName != null && !roomName.equals(roomManager.getCurrentRoom())) {
                    roomManager.incrementUnreadCount(roomName);
    
                    // 使用正确的通知方法
                    notificationManager.showNotification(
                        "来自 " + roomName + " 的新消息",
                        sender + ": " + content,
                        NotificationManager.NotificationType.MESSAGE
                    );
                }
                break;
            }
    
            case Message.TYPE_RICH_MESSAGE: {
                String roomName = (String) message.get("room");
                String richContent = (String) message.get("content");
                String richSender = (String) message.get("sender");
                String hasEmojiStr = (String) message.get("hasEmoji");
                boolean hasEmoji = "true".equals(hasEmojiStr);
    
                // 显示富文本消息
                displayRichMessage(richContent, Color.BLACK, hasEmoji);
    
                // 如果消息不是当前聊天室的，增加未读消息计数并发送通知
                if (roomName != null && !roomName.equals(roomManager.getCurrentRoom())) {
                    roomManager.incrementUnreadCount(roomName);
    
                    // 发送通知
                    if (richSender != null && !richSender.equals(username)) {
                        notificationManager.showNotification(
                            "来自 " + roomName + " 的新消息",
                            richSender + ": " + richContent,
                            NotificationManager.NotificationType.MESSAGE
                        );
                    }
                }
                break;
            }
    
            case Message.TYPE_SYSTEM: {
                String sysMsg = (String) message.get("content");
                if (sysMsg.contains("加入了房间")) {
                    String joinUsername = sysMsg.substring(0, sysMsg.indexOf("加入"));
                    displaySystemMessage(joinUsername + " 加入了聊天室");
                    
                    notificationManager.showNotification(
                        "系统通知", 
                        joinUsername + " 加入了聊天室", 
                        NotificationManager.NotificationType.USER_JOIN
                    );
                } else if (sysMsg.contains("离开了房间")) {
                    String leaveUsername = sysMsg.substring(0, sysMsg.indexOf("离开"));
                    displaySystemMessage(leaveUsername + " 离开了聊天室");
                    
                    notificationManager.showNotification(
                        "系统通知", 
                        leaveUsername + " 离开了聊天室", 
                        NotificationManager.NotificationType.USER_LEAVE
                    );
                } else {
                    displaySystemMessage(sysMsg);
                    
                    notificationManager.showNotification(
                        "系统通知", 
                        sysMsg, 
                        NotificationManager.NotificationType.SYSTEM
                    );
                }
                break;
            }

            case Message.TYPE_USER_LIST:
                // 更新用户列表
                SwingUtilities.invokeLater(() -> {
                    // 获取用户列表字符串
                    String usersStr = (String) message.get("users");
                
                // 如果用户列表不为空，更新用户列表面板
                if (usersStr != null && !usersStr.isEmpty()) {
                    String[] users = usersStr.split(",");
                    userListPanel.updateUserList(users);
                }
            });
            break;
            
            case Message.TYPE_ROOM_LIST: {
                String roomsStr = (String) message.get("rooms");
                if (roomsStr != null && !roomsStr.isEmpty()) {
                    String[] roomInfos = roomsStr.split(",");
                    for (String roomInfo : roomInfos) {
                        String[] parts = roomInfo.split(":");
                        if (parts.length >= 2) {
                            String name = parts[0];
                            String typeStr = parts[1];
                            
                            // 转换类型字符串为枚举类型
                            RoomListPanel.RoomType roomType = RoomListPanel.RoomType.PUBLIC;
                            if ("private".equals(typeStr)) {
                                roomType = RoomListPanel.RoomType.PRIVATE;
                            } else if ("password".equals(typeStr)) {
                                roomType = RoomListPanel.RoomType.PASSWORD;
                            }
                            
                            // 将房间添加到列表
                            roomListPanel.addRoom(name, roomType);
                        }
                    }
                }
                break;
            }
            
        case Message.TYPE_STATUS_UPDATE:
            // 更新用户状态
            String statusUser = (String) message.get("username");
            String statusValue = (String) message.get("status");
            
            if (statusUser != null && statusValue != null) {
                UserListPanel.UserStatus status = UserListPanel.UserStatus.ONLINE;
                
                if ("away".equals(statusValue)) {
                    status = UserListPanel.UserStatus.AWAY;
                } else if ("busy".equals(statusValue)) {
                    status = UserListPanel.UserStatus.BUSY;
                } else if ("offline".equals(statusValue)) {
                    status = UserListPanel.UserStatus.OFFLINE;
                }
                
                userListPanel.setUserStatus(statusUser, status);
            }
            break;
            
        case Message.TYPE_HEARTBEAT:
            // 心跳消息，不需要处理
            break;

        case Message.TYPE_KEY_EXCHANGE:
             handleKeyExchangeMessage(message);
             break;

        case Message.TYPE_ENCRYPTED:
             handleEncryptedMessage(message);
             break;
        default:
            // 未知消息类型
            break;
    }

}
    // 处理密钥交换消息
    private void handleKeyExchangeMessage(Message msg) {
        try {
            // 1. 解析对方公钥
            String publicKeyStr = (String) msg.get("publicKey");
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
            PublicKey peerPublicKey = KeyFactory.getInstance("DH").generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            // 2. 生成共享密钥
            sharedSecretKey = CryptoUtils.generateSharedSecret(dhKeyPair.getPrivate(), peerPublicKey);
            isEncrypted = true;

            // 更新UI状态
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("加密通信已启用");
            });
        } catch (Exception e) {
            displaySystemMessage("密钥协商失败: " + e.getMessage());
        }
    }

    // 处理加密消息
    private void handleEncryptedMessage(Message msg) {
        try {
            // 1. 提取加密数据和IV
            String encryptedPayload = (String) msg.get("payload");
            String ivStr = (String) msg.get("iv");
            byte[] ivBytes = Base64.getDecoder().decode(ivStr);

            // 2. 解密消息
            String decrypted = CryptoUtils.decrypt(encryptedPayload, sharedSecretKey, ivBytes);

            // 3. 显示解密后的消息
            String sender = (String) msg.get("sender");
            String formattedMsg = "[" + sender + "] " + decrypted;
            displayMessage(formattedMsg, Color.BLUE);
        } catch (Exception e) {
            displaySystemMessage("解密失败: " + e.getMessage());
        }
    }

    // 实现 ChatClient.ConnectionListener 接口方法
    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("已连接到服务器");
            statusLabel.setForeground(Color.GREEN);
        });
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("已断开连接");
            statusLabel.setForeground(Color.RED);
        });
    }

    @Override
    public void onReconnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("已重新连接");
            statusLabel.setForeground(Color.GREEN);
        });
    }

    @Override
    public void onConnectionFailed(String reason) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("连接失败: " + reason);
            statusLabel.setForeground(Color.RED);
        });
    }

    @Override
    public void onReconnectFailed() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("重连失败");
            statusLabel.setForeground(Color.RED);
        });
    }

    // 实现 EmojiSelector.EmojiListener 接口方法
    @Override
    public void onEmojiSelected(String emoji) {
        inputField.setText(inputField.getText() + emoji);
    }

    // 实现 ChatClient.MessageListener 接口方法
    @Override
    public void onMessageReceived(String message) {
        try {
            Message msg = MessageParser.parseMessage(message);
            if (msg != null) {
                processMessage(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void onLoginSuccess() {
        try {
            // 1. 生成DH密钥对
            dhKeyPair = CryptoUtils.generateDHKeyPair();
            String publicKeyStr = Base64.getEncoder().encodeToString(dhKeyPair.getPublic().getEncoded());

            // 2. 发送密钥交换消息给目标用户（假设目标用户为固定值或通过UI选择）
            String targetUser = "Bob"; // 示例：固定目标用户
            Message keyMsg = Message.createKeyExchangeMessage(publicKeyStr, "DH", targetUser);
            client.sendMessage(keyMsg.toJson());

            // 3. 生成随机IV
            iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            // 更新UI状态
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("已登录，正在协商加密密钥...");
            });
        } catch (Exception e) {
            displaySystemMessage("密钥生成失败: " + e.getMessage());
        }
    }
}