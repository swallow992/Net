package com.example.chat.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 表情选择器组件
 */
public class EmojiSelector extends JPanel {
    public static final String[] EMOJIS = {
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆",
        "😉", "😊", "😋", "😎", "😍", "😘", "😗", "😙",
        "😚", "🙂", "🤔", "😐", "😑", "😶", "🙄", "😏",
        "😣", "😥", "😮", "🤐", "😯", "😪", "😫", "😴"
    };
    
    private EmojiListener listener;
    
    public EmojiSelector() {
        setLayout(new GridLayout(4, 8, 2, 2));
        setBorder(BorderFactory.createTitledBorder("表情"));
        
        for (String emoji : EMOJIS) {
            JButton button = new JButton(emoji);
            button.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            button.setFocusPainted(false);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (listener != null) {
                        listener.onEmojiSelected(emoji);
                    }
                }
            });
            add(button);
        }
    }
    
    public void setEmojiListener(EmojiListener listener) {
        this.listener = listener;
    }
    
    /**
     * 表情选择监听器接口
     */
    public interface EmojiListener {
        void onEmojiSelected(String emoji);
    }
}