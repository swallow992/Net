// LoginGUI.java
package com.example.chat.client;

import com.example.chat.common.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * 登录界面
 */
public class LoginGUI extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;
    private ChatClient client;
    // 数据库连接信息（移到配置文件中更佳）
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "net";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    public LoginGUI(String host, int port) {
        setTitle("NIO 聊天系统 - 登录");
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 初始化客户端
        client = new ChatClient(host, port);

        initUI();
    }

    private void initUI() {
        // 创建面板
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 标题
        JLabel titleLabel = new JLabel("NIO 实时聊天系统", JLabel.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);

        // 用户名
        JLabel usernameLabel = new JLabel("用户名:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(usernameLabel, gbc);

        usernameField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(usernameField, gbc);

        // 密码
        JLabel passwordLabel = new JLabel("密码:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(passwordLabel, gbc);

        passwordField = new JPasswordField(15);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(passwordField, gbc);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginButton = new JButton("登录");
        registerButton = new JButton("注册");
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        // 状态标签
        statusLabel = new JLabel("", JLabel.CENTER);
        statusLabel.setForeground(Color.RED);
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(statusLabel, gbc);

        // 添加事件监听
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                login();
            }
        });

        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                register();
            }
        });

        getContentPane().add(panel);
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) {
            statusLabel.setText("请输入用户名");
            return;
        }

        if (password.isEmpty()) {
            statusLabel.setText("请输入密码");
            return;
        }

        // 连接服务器
        statusLabel.setText("正在连接服务器...");
        if (!client.connect()) {
            statusLabel.setText("连接服务器失败");
            return;
        }

        // 发送登录消息
        try {
            Message loginMessage = Message.createLoginMessage(username, password);
            client.sendMessage(loginMessage.toJson());

            // 等待服务器响应
            new Thread(() -> {
                try {
                    while (true) {  // 添加循环以持续接收消息
                        String response = client.receiveMessage();
                        if (response != null) {
                            Message message = com.example.chat.common.MessageParser.parseMessage(response);
                            if (message != null && Message.TYPE_SYSTEM.equals(message.getType())) {
                                String content = (String) message.get("content");
                                if (content.contains("登录成功")) {
                                    // 登录成功，设置用户名并打开聊天界面
                                    client.setUsername(username);
                                    SwingUtilities.invokeLater(() -> {
                                        ChatClientGUI chatGUI = new ChatClientGUI(client, username);
                                        chatGUI.setVisible(true);
                                        dispose();
                                    });
                                    break;  // 登录成功后退出循环
                                } else {
                                    SwingUtilities.invokeLater(() -> {
                                        statusLabel.setText(content);
                                    });
                                }
                            }
                        }
                        Thread.sleep(100);  // 添加短暂延迟避免CPU占用过高
                    }
                } catch (IOException | InterruptedException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("登录失败: " + e.getMessage());
                    });
                }
            }).start();

        } catch (IOException e) {
            statusLabel.setText("登录失败: " + e.getMessage());
        }
    }

    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) {
            statusLabel.setText("请输入用户名");
            return;
        }

        if (password.isEmpty()) {
            statusLabel.setText("请输入密码");
            return;
        }

        // 连接服务器
        statusLabel.setText("正在连接服务器...");
        if (!client.connect()) {
            statusLabel.setText("连接服务器失败");
            return;
        }

        // 发送注册消息
        try {
            Message registerMessage = Message.createRegisterMessage(username, password);
            client.sendMessage(registerMessage.toJson());

            // 等待服务器响应
            new Thread(() -> {
                try {
                    String response = client.receiveMessage();
                    if (response != null) {
                        Message message = com.example.chat.common.MessageParser.parseMessage(response);
                        if (message != null && Message.TYPE_SYSTEM.equals(message.getType())) {
                            String content = (String) message.get("content");
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText(content);
                            });
                        }
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("注册失败: " + e.getMessage());
                    });
                }
            }).start();

        } catch (IOException e) {
            statusLabel.setText("注册失败: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginGUI loginGUI = new LoginGUI("localhost", 8080);
            loginGUI.setVisible(true);
        });
    }
}

