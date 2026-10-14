package com.orangecode.agent.model.entity;

import com.orangecode.agent.service.ChatService;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@Setter
public class SessionInfo {
    public final int MAX_WINDOW_SIZE;
    public final String sessionId;
    public final List<Map<String, String>> messageHistory;
    public final Long createTime;
    public final ReentrantLock lock;
    public final ChatService chatService;

    public SessionInfo(String sessionId, ChatService chatService){
        this.sessionId = sessionId;
        this.messageHistory = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
        this.chatService = chatService;
        this.MAX_WINDOW_SIZE = 5;
    }

    public void addMessage(String question, String answer) {
        lock.lock();
        try {
            // 添加用户消息
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messageHistory.add(userMsg);

            // 添加AI回复
            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", answer);
            messageHistory.add(assistantMsg);

            // 自动压缩：保持最多 MAX_WINDOW_SIZE 对消息
            // 每对消息包含2条记录（user + assistant）
//            int maxMessages = MAX_WINDOW_SIZE * 2;
//            if (messageHistory.size() > maxMessages) {
//                compressHistory();
//            }

        } finally {
            lock.unlock();
        }
    }

    public int getMessagePairCount() {
        lock.lock();
        try {
            return messageHistory.size() / 2;
        } finally {
            lock.unlock();
        }
    }
}
