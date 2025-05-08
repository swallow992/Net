package com.example.chat.client;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通知管理类，用于处理系统消息和提醒
 */
public class NotificationManager {
    private JFrame parentFrame;
    private boolean soundEnabled = true;
    private boolean popupEnabled = true;
    private ExecutorService soundExecutor;
    
    // 通知类型
    public enum NotificationType {
        MESSAGE("新消息", ""),
        SYSTEM("系统消息", ""),
        ERROR("错误", ""),
        USER_JOIN("用户加入", ""),
        USER_LEAVE("用户离开", "");
        
        private final String displayName;
        private final String soundPath;
        
        NotificationType(String displayName, String soundPath) {
            this.displayName = displayName;
            this.soundPath = soundPath;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getSoundPath() {
            return soundPath;
        }
    }
    
    public NotificationManager(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        this.soundExecutor = Executors.newSingleThreadExecutor();
    }
    
    // 显示通知
    public void showNotification(String title, String message, NotificationType type) {
        // 移除声音播放功能，只显示弹窗
        if (popupEnabled) {
            showPopup(title, message, type);
        }
    }
    
    // 移除 playSound 方法，因为不再需要
    
    // 设置声音启用状态
    public void setSoundEnabled(boolean enabled) {
        // 移除此功能，因为不再需要声音
    }
    
    public void setPopupEnabled(boolean enabled) {
    
    // 设置弹窗启用状态
    // 删除此处的重复方法声明，因为在文件后面已经有一个相同的setPopupEnabled方法
        this.popupEnabled = enabled;
    }
    
    // 播放声音
    private void playSound(String soundPath) {
        soundExecutor.submit(() -> {
            try (InputStream is = getClass().getResourceAsStream("/sounds"+soundPath)) { // 修复路径
                if (is == null) {
                    System.err.println("找不到声音文件: " + soundPath);
                    return;
                }
                
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(is);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                clip.start();
                
                // 等待声音播放完毕
                Thread.sleep(clip.getMicrosecondLength() / 1000);
                clip.close();
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
    
    // 显示弹窗通知
    private void showPopup(String title, String message, NotificationType type) {
        SwingUtilities.invokeLater(() -> {
            // 创建通知窗口
            JDialog dialog = new JDialog(parentFrame, title, false);
            dialog.setSize(300, 100);
            dialog.setLayout(new BorderLayout());
            
            // 设置位置（右下角）
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setLocation(
                    screenSize.width - dialog.getWidth() - 20,
                    screenSize.height - dialog.getHeight() - 50);
            
            // 创建内容面板
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // 图标
            JLabel iconLabel = new JLabel();
            iconLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
            
            // 消息
            JLabel messageLabel = new JLabel("<html><body>" + message + "</body></html>");
            
            panel.add(iconLabel, BorderLayout.WEST);
            panel.add(messageLabel, BorderLayout.CENTER);
            
            dialog.add(panel, BorderLayout.CENTER);
            
            // 自动关闭
            Timer timer = new Timer(5000, e -> dialog.dispose());
            timer.setInitialDelay(3000); // 新增：缩短显示时间
            timer.setRepeats(false);
            timer.start();
            
            dialog.setVisible(true);
        });
    }
    
    
    // 创建设置菜单
    public JMenu createSettingsMenu() {
        JMenu settingsMenu = new JMenu("通知设置");
        
        JCheckBoxMenuItem soundItem = new JCheckBoxMenuItem("启用声音", soundEnabled);
        soundItem.addActionListener(e -> setSoundEnabled(soundItem.isSelected()));
        
        JCheckBoxMenuItem popupItem = new JCheckBoxMenuItem("启用弹窗", popupEnabled);
        popupItem.addActionListener(e -> setPopupEnabled(popupItem.isSelected()));
        
        settingsMenu.add(soundItem);
        settingsMenu.add(popupItem);
        
        return settingsMenu;
    }
    
    // 关闭资源
    public void close() {
        soundExecutor.shutdown();
    }
}