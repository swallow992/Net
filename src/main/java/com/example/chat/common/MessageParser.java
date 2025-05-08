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
            Map<String, Object> fields = parseJson(json);
            String type = (String) fields.get("type");
            if (type == null) {
                return null;
            }

            Message message = new Message(type);
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String key = entry.getKey();
                if (!"type".equals(key)) {
                    message.put(key, entry.getValue()); // 直接存入原始值（Object类型）
                }
            }

            // 根据类型调用静态方法创建特定消息对象
            switch (type) {
                case Message.TYPE_KEY_EXCHANGE:
                    return Message.createKeyExchangeMessage(
                            (String) message.get("publicKey"),
                            (String) message.get("algorithm"),
                            (String) message.get("target")
                    );
                case Message.TYPE_ENCRYPTED:
                    return Message.createEncryptedMessage(
                            (String) message.get("payload"),
                            (String) message.get("iv"),
                            (String) message.get("target")
                    );
                default:
                    return message; // 其他类型保持原有逻辑
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 简单的JSON解析方法
     * @param json JSON字符串
     * @return 字段名和值的映射
     */
    private static Map<String, Object> parseJson(String json) { // 修改返回值为 Object 类型
        Map<String, Object> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        } else {
            return result;
        }

        List<String> fields = splitJsonFields(json);
        for (String field : fields) {
            int colonIndex = field.indexOf(':');
            if (colonIndex > 0) {
                String key = removeQuotes(field.substring(0, colonIndex).trim());
                String valueStr = removeQuotes(field.substring(colonIndex + 1).trim());

                // 尝试解析值的类型（字符串、布尔、数字等）
                Object value;
                if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
                    value = valueStr.substring(1, valueStr.length() - 1); // 纯字符串
                } else if (valueStr.equals("true") || valueStr.equals("false")) {
                    value = Boolean.parseBoolean(valueStr); // 布尔值
                } else if (valueStr.matches("-?\\d+(\\.\\d+)?")) { // 数字
                    if (valueStr.contains(".")) {
                        value = Double.parseDouble(valueStr);
                    } else {
                        value = Integer.parseInt(valueStr);
                    }
                } else {
                    value = valueStr; // 默认保留为字符串
                }
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