package com.example.chat.client;

import com.example.chat.common.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatClient {
    private SocketChannel socketChannel;
    private String serverHost;
    private int serverPort;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private AtomicBoolean reconnecting = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private MessageListener messageListener;
    private ConnectionListener connectionListener;
    private String username; // 添加用户名字段
    
    // 心跳间隔（毫秒）
    private static final long HEARTBEAT_INTERVAL = 30000;
    // 重连间隔（毫秒）
    private static final long RECONNECT_INTERVAL = 5000;
    // 最大重连次数
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private int reconnectAttempts = 0;

    public ChatClient(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public boolean connect() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(serverHost, serverPort));
            socketChannel.configureBlocking(false);
            connected.set(true);
            reconnectAttempts = 0;
            
            // 启动心跳任务
            startHeartbeat();
            
            if (connectionListener != null) {
                connectionListener.onConnected();
            }
            
            return true;
        } catch (IOException e) {
            connected.set(false);
            if (connectionListener != null) {
                connectionListener.onConnectionFailed(e.getMessage());
            }
            return false;
        }
    }
    
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (connected.get()) {
                try {
                    sendMessage(Message.createHeartbeatMessage().toJson());
                } catch (IOException e) {
                    handleDisconnect();
                }
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void handleDisconnect() {
        if (connected.compareAndSet(true, false)) {
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
            tryReconnect();
        }
    }
    
    private void tryReconnect() {
        if (reconnecting.compareAndSet(false, true) && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            scheduler.schedule(() -> {
                try {
                    if (socketChannel != null) {
                        try {
                            socketChannel.close();
                        } catch (IOException e) {
                            // 忽略关闭异常
                        }
                    }
                    
                    if (connect()) {
                        reconnecting.set(false);
                        if (connectionListener != null) {
                            connectionListener.onReconnected();
                        }
                    } else {
                        reconnecting.set(false);
                        tryReconnect();
                    }
                } catch (Exception e) {
                    reconnecting.set(false);
                    tryReconnect();
                }
            }, RECONNECT_INTERVAL, TimeUnit.MILLISECONDS);
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            reconnecting.set(false);
            if (connectionListener != null) {
                connectionListener.onReconnectFailed();
            }
        }
    }

    public void sendMessage(String json) throws IOException {
        if (!connected.get()) {
            throw new IOException("未连接到服务器");
        }
        ByteBuffer buffer = ByteBuffer.wrap(json.getBytes());
        socketChannel.write(buffer);
    }

    public String receiveMessage() throws IOException {
        if (!connected.get()) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int read = socketChannel.read(buffer);
        if (read > 0) {
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes);
        } else if (read < 0) {
            // 服务器关闭连接
            handleDisconnect();
            return null;
        }
        return null;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }
    
    public boolean isConnected() {
        return connected.get();
    }
    
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }
    
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void close() {
        connected.set(false);
        scheduler.shutdownNow();
        try {
            if (socketChannel != null) socketChannel.close();
        } catch (IOException e) {
            // ignore
        }
    }
    
    /**
     * 设置当前用户名
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * 获取当前用户名
     * @return 当前用户名
     */
    public String getUsername() {
        return username;
    }
    
    // 消息监听器接口
    public interface MessageListener {
        void onMessageReceived(String message);
    }
    
    // 连接状态监听器接口
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onReconnected();
        void onConnectionFailed(String reason);
        void onReconnectFailed();
    }
}