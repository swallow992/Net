package com.example.chat.server;

import com.example.chat.common.Message;
import com.example.chat.common.MessageParser;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChatServer {

    private static final int PORT = 8080;
    private Selector selector;
    // 保存每个客户端所属的聊天室
    private Map<SocketChannel, String> clientRooms = new HashMap<>();
    // 聊天室及其成员
    private Map<String, Set<SocketChannel>> rooms = new HashMap<>();
    // 保存用户名和对应的通道
    private Map<String, SocketChannel> userChannels = new HashMap<>();
    // 保存通道和对应的用户名
    private Map<SocketChannel, String> channelUsers = new HashMap<>();
    // 定时任务执行器
    private ScheduledExecutorService scheduler;
    // 数据库连接信息
    private static final String DB_URL = "jdbc:mysql://localhost:3306/net";  // 更改为你的数据库URL
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";
    private static Connection dbConnection;

    public static void main(String[] args) {
        new ChatServer().start();
    }

    public void start() {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(PORT));
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            // 初始化定时任务执行器
            scheduler = Executors.newScheduledThreadPool(1);

            // 启动定时任务，定期清理断开连接的客户端
            scheduler.scheduleAtFixedRate(this::cleanupDisconnectedClients, 60, 60, TimeUnit.SECONDS);

            // 建立数据库连接
            try {
                Class.forName("com.mysql.cj.jdbc.Driver"); // 注册 JDBC 驱动，根据你使用的数据库驱动更改
                dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                System.out.println("成功连接到数据库！");
            } catch (ClassNotFoundException | SQLException e) {
                System.err.println("无法连接到数据库: " + e.getMessage());
                // 考虑是否应该终止服务器启动
                return;
            }

            System.out.println("Server started on port " + PORT);

            while (true) {
                selector.select();
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isAcceptable()) {
                        handleAccept(serverSocketChannel);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            // 关闭数据库连接
            if (dbConnection != null) {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void cleanupDisconnectedClients() {
        List<SocketChannel> disconnectedChannels = new ArrayList<>();

        // 检查所有客户端连接
        for (SocketChannel channel : clientRooms.keySet()) {
            try {
                ByteBuffer testBuffer = ByteBuffer.allocate(1);
                int read = channel.read(testBuffer);
                if (read < 0) {
                    disconnectedChannels.add(channel);
                }
            } catch (IOException e) {
                disconnectedChannels.add(channel);
            }
        }

        // 断开连接的客户端
        for (SocketChannel channel : disconnectedChannels) {
            disconnect(channel);
        }
    }

    private void handleAccept(ServerSocketChannel serverSocketChannel) throws IOException {
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));

        // 连接时不立即加入聊天室，等待登录消息
        System.out.println("New client connected: " + clientChannel.getRemoteAddress());

        // 发送欢迎消息
        Message welcomeMessage = Message.createSystemMessage("欢迎连接到NIO聊天服务器，请登录");
        clientChannel.write(ByteBuffer.wrap(welcomeMessage.toJson().getBytes()));
    }

    private void handleRead(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                disconnect(clientChannel);
                return;
            }
            if (bytesRead > 0) {
                buffer.flip();
                String json = new String(buffer.array(), 0, buffer.limit()).trim();
                buffer.clear();

                // 使用消息解析器解析JSON消息
                Message message = MessageParser.parseMessage(json);
                if (message == null) {
                    return;
                }

                // 根据消息类型处理
                String type = message.getType();
                switch (type) {
                    case Message.TYPE_LOGIN:
                        handleLogin(clientChannel, message);
                        break;

                    case Message.TYPE_REGISTER:
                        handleRegister(clientChannel, message);
                        break;

                    case Message.TYPE_MESSAGE:
                        handleChatMessage(clientChannel, message);
                        break;

                    case Message.TYPE_RICH_MESSAGE:
                        handleRichMessage(clientChannel, message);
                        break;

                    case Message.TYPE_SWITCH_ROOM:
                        handleSwitchRoom(clientChannel, message);
                        break;

                    case Message.TYPE_HEARTBEAT:
                        // 心跳消息，不需要特殊处理
                        break;

                    default:
                        System.out.println("未知消息类型: " + type);
                        break;
                }
            }
        } catch (IOException e) {
            disconnect(clientChannel);
        }
    }

    private void handleLogin(SocketChannel clientChannel, Message message) throws IOException {
        String username = (String) message.get("username");
        String password = (String) message.get("password");

        try {
            // 使用预处理语句防止 SQL 注入
            String query = "SELECT password FROM users WHERE username = ?";
            PreparedStatement preparedStatement = dbConnection.prepareStatement(query);
            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                String storedPassword = resultSet.getString("password");
                // 直接比较密码 (不安全!)
                if (storedPassword.equals(password)) {
                    // 登录成功
                    Message response = Message.createSystemMessage("登录成功！");
                    response.send(clientChannel);
                    // 将用户添加到在线用户列表
                    userChannels.put(username, clientChannel);
                    channelUsers.put(clientChannel, username);
                    System.out.println("用户 " + username + " 登录成功");

                    // 默认加入大厅
                    switchRoom(clientChannel, "大厅");

                    // 发送在线用户列表
                    sendUserList("大厅");
                } else {
                    Message response = Message.createSystemMessage("登录失败：密码错误");
                    response.send(clientChannel);
                    System.out.println("用户 " + username + " 登录失败：密码错误");
                }
            } else {
                Message response = Message.createSystemMessage("登录失败：用户不存在");
                response.send(clientChannel);
                System.out.println("用户 " + username + " 登录失败：用户不存在");
            }
            resultSet.close();
            preparedStatement.close();
        } catch (SQLException e) {
            Message response = Message.createSystemMessage("登录错误：" + e.getMessage());
            response.send(clientChannel);
            System.err.println("登录数据库错误: " + e.getMessage());
        }
    }

    private void handleRegister(SocketChannel clientChannel, Message message) throws IOException {
        String username = (String) message.get("username");
        String password = (String) message.get("password");

        try {
            // 检查用户名是否已存在
            String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
            PreparedStatement checkStatement = dbConnection.prepareStatement(checkQuery);
            checkStatement.setString(1, username);
            ResultSet resultSet = checkStatement.executeQuery();
            resultSet.next();
            int userCount = resultSet.getInt(1);
            resultSet.close();
            checkStatement.close();

            if (userCount > 0) {
                Message response = Message.createSystemMessage("注册失败：用户名已存在");
                response.send(clientChannel);
                System.out.println("用户 " + username + " 注册失败：用户名已存在");
                return;
            }

            // 直接存储密码 (不安全!)
            String insertQuery = "INSERT INTO users (username, password) VALUES (?, ?)";
            PreparedStatement insertStatement = dbConnection.prepareStatement(insertQuery);
            insertStatement.setString(1, username);
            insertStatement.setString(2, password);
            insertStatement.executeUpdate();
            insertStatement.close();

            Message response = Message.createSystemMessage("注册成功！");
            response.send(clientChannel);
            System.out.println("用户 " + username + " 注册成功");

        } catch (SQLException e) {
            Message response = Message.createSystemMessage("注册错误：" + e.getMessage());
            response.send(clientChannel);
            System.err.println("注册数据库错误: " + e.getMessage());
        }
    }

    private void handleChatMessage(SocketChannel clientChannel, Message message) throws IOException {
        String room = (String) message.get("room");
        String content = (String) message.get("content");
        String username = channelUsers.get(clientChannel);

        if (room != null && content != null && username != null) {
            // 创建新消息并发送到房间
            Message chatMessage = new Message(Message.TYPE_MESSAGE);
            chatMessage.put("content", "[" + username + "] " + content);
            sendMessageToRoom(room, chatMessage);
        }
    }

    private void handleRichMessage(SocketChannel clientChannel, Message message) throws IOException {
        String room = (String) message.get("room");
        String content = (String) message.get("content");
        String hasEmoji = (String) message.get("hasEmoji");
        String username = channelUsers.get(clientChannel);

        if (room != null && content != null && username != null) {
            // 创建新的富文本消息并发送到房间
            Message richMessage = new Message(Message.TYPE_RICH_MESSAGE);
            richMessage.put("content", "[" + username + "] " + content);
            richMessage.put("hasEmoji", hasEmoji);
            sendMessageToRoom(room, richMessage);
        }
    }

    private void handleSwitchRoom(SocketChannel clientChannel, Message message) throws IOException {
        String newRoom = (String) message.get("room");
        if (newRoom != null && !newRoom.isEmpty()) {
            switchRoom(clientChannel, newRoom);

            // 发送在线用户列表
            sendUserList(newRoom);
        }
    }

    private void sendUserList(String room) {
        Set<SocketChannel> clients = rooms.get(room);
        if (clients != null && !clients.isEmpty()) {
            // 构建用户列表
            StringBuilder userList = new StringBuilder();
            for (SocketChannel client : clients) {
                String username = channelUsers.get(client);
                if (username != null) {
                    if (userList.length() > 0) {
                        userList.append(",");
                    }
                    userList.append(username);
                }
            }

            // 创建用户列表消息
            Message message = new Message(Message.TYPE_USER_LIST);
            message.put("users", userList.toString());

            // 发送给房间内所有用户
            sendMessageToRoom(room, message);
        }
    }

    private void switchRoom(SocketChannel clientChannel, String newRoom) throws IOException {
        String oldRoom = clientRooms.get(clientChannel);
        String username = channelUsers.get(clientChannel);

        if (oldRoom != null) {
            rooms.get(oldRoom).remove(clientChannel);

            // 创建系统消息
            Message message = new Message(Message.TYPE_SYSTEM);
            if (username != null) {
                message.put("content", "用户 " + username + " 离开了房间");
            } else {
                message.put("content", "有用户离开了房间");
            }
            sendMessageToRoom(oldRoom, message);

            // 更新旧房间的用户列表
            sendUserList(oldRoom);
        }

        clientRooms.put(clientChannel, newRoom);
        rooms.computeIfAbsent(newRoom, k -> new HashSet<>()).add(clientChannel);

        // 创建系统消息
        Message message = new Message(Message.TYPE_SYSTEM);
        if (username != null) {
            message.put("content", "用户 " + username + " 加入了房间");
        } else {
            message.put("content", "有用户加入了房间");
        }
        sendMessageToRoom(newRoom, message);
    }

    private void sendToRoom(String room, String msg) {
        // 创建系统消息
        Message message = new Message(Message.TYPE_SYSTEM);
        message.put("content", msg);
        sendMessageToRoom(room, message);
    }

    private void sendMessageToRoom(String room, Message message) {
        Set<SocketChannel> clients = rooms.get(room);
        if (clients != null) {
            try {
                for (SocketChannel client : clients) {
                    message.send(client);
                }
            } catch (IOException e) {
                // 处理发送消息时可能出现的异常
                e.printStackTrace();
            }
        }
    }

    /**
     * 向所有聊天室发送系统消息
     */
    private void broadcastSystemMessage(String content) throws IOException {
        Message systemMessage = Message.createSystemMessage(content);

        for (Set<SocketChannel> members : rooms.values()) {
            for (SocketChannel member : members) {
                try {
                    systemMessage.send(member);
                } catch (IOException e) {
                    // 发送失败，可能客户端已断开连接
                    disconnect(member);
                }
            }
        }
    }

    private void disconnect(SocketChannel clientChannel) {
        try {
            String room = clientRooms.get(clientChannel);
            String username = channelUsers.get(clientChannel);

            if (room != null) {
                rooms.get(room).remove(clientChannel);

                // 创建系统消息
                Message message = Message.createSystemMessage("用户 " + username + " 断开连接");
                if (username == null){
                    message = Message.createSystemMessage("有用户断开连接");
                }
                sendMessageToRoom(room, message);

                // 更新用户列表
                sendUserList(room);
            }

            // 清理资源
            clientRooms.remove(clientChannel);
            channelUsers.remove(clientChannel);
            clientChannel.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

