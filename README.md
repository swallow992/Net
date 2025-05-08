# Java NIO 实时聊天系统

这是一个基于Java NIO（非阻塞IO）实现的实时聊天系统，支持多聊天室、用户管理和断线重连等功能。

## 功能特点

- 基于Java NIO实现的高性能服务器
- 支持多聊天室功能
- JSON格式的消息协议
- 用户登录和身份验证
- 实时显示在线用户列表
- 心跳机制保持连接
- 断线自动重连
- 美观的GUI界面

## 系统架构

- `com.example.chat.server`: 服务器端实现
- `com.example.chat.client`: 客户端实现
- `com.example.chat.common`: 公共组件（消息协议等）

## 消息协议

系统使用JSON格式的消息协议，主要消息类型包括：

- 登录消息 (login)
- 聊天消息 (message)
- 切换房间消息 (switch_room)
- 用户列表消息 (user_list)
- 系统消息 (system)
- 心跳消息 (heartbeat)

## 使用方法

### 启动服务器

```bash
java -cp <classpath> com.example.chat.server.ChatServer
```

### 启动客户端

```bash
java -cp <classpath> com.example.chat.client.ChatClientGUI
```

## 客户端使用说明

1. 启动客户端后，输入用户名和密码登录
2. 登录成功后，默认进入"大厅"聊天室
3. 可以通过顶部的下拉菜单切换不同的聊天室
4. 右侧显示当前聊天室的在线用户列表
5. 在底部输入框中输入消息，点击"发送"按钮或按回车键发送消息
6. 状态栏显示当前连接状态

## 技术实现

- 使用Java NIO的Selector、Channel等组件实现非阻塞IO
- 使用Swing构建图形界面
- 实现心跳机制确保连接稳定
- 断线重连机制提高系统可靠性
- 自定义消息协议实现客户端与服务端通信