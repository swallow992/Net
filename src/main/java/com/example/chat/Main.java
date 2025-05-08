package com.example.chat;

import com.example.chat.client.LoginGUI;
import com.example.chat.server.ChatServer;

import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * 主类，用于启动聊天系统
 */
public class Main {
    public static void main(String[] args) {
        // 显示选择对话框
        String[] options = {"启动服务器", "启动客户端", "同时启动服务器和客户端"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "请选择启动模式",
                "NIO 聊天系统",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        
        switch (choice) {
            case 0: // 启动服务器
                startServer();
                break;
                
            case 1: // 启动客户端
                startClient();
                break;
                
            case 2: // 同时启动服务器和客户端
                startServer();
                startClient();
                break;
                
            default:
                System.exit(0);
        }
    }
    
    private static void startServer() {
        Thread serverThread = new Thread(() -> {
            ChatServer server = new ChatServer();
            server.start();
        });
        serverThread.start();
        
        // 添加短暂延迟确保服务器完全启动
        try {
            Thread.sleep(2000); // 等待2秒确保服务器启动
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private static void startClient() {
        // 等待一点时间确保服务器准备好
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            LoginGUI loginGUI = new LoginGUI("localhost", 8080);
            loginGUI.setVisible(true);
        });
    }
}