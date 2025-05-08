package com.example.chat.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息解析器，用于解析JSON格式的消息
 */
public class MessageParser {
    
    /**
     * 解析JSON字符串为消息对象
     * @param json JSON字符串
     * @return 消息对象
     */
    public static Message parseMessage(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            // 简单解析JSON，提取type和其他字段
            Map<String, String> fields = parseJson(json);
            String type = fields.get("type");
            if (type == null) {
                return null;
            }
            
            Message message = new Message(type);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (!"type".equals(entry.getKey())) {
                    message.put(entry.getKey(), entry.getValue());
                }
            }
            
            return message;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 简单的JSON解析方法
     * @param json JSON字符串
     * @return 字段名和值的映射
     */
    private static Map<String, String> parseJson(String json) {
        Map<String, String> result = new HashMap<>();
        
        // 去掉首尾的花括号
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        } else {
            return result;
        }
        
        // 解析字段
        List<String> fields = splitJsonFields(json);
        for (String field : fields) {
            int colonIndex = field.indexOf(':');
            if (colonIndex > 0) {
                String key = field.substring(0, colonIndex).trim();
                String value = field.substring(colonIndex + 1).trim();
                
                // 去掉引号
                key = removeQuotes(key);
                value = removeQuotes(value);
                
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * 分割JSON字段
     */
    private static List<String> splitJsonFields(String json) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        char previousChar = 0;
        
        for (char c : json.toCharArray()) {
            if (c == '"' && previousChar != '\\') {
                inQuotes = !inQuotes;
            }
            
            if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
            
            previousChar = c;
        }
        
        if (currentField.length() > 0) {
            fields.add(currentField.toString().trim());
        }
        
        return fields;
    }
    
    /**
     * 去掉字符串的引号
     */
    private static String removeQuotes(String str) {
        str = str.trim();
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        }
        return str;
    }
}