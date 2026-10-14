package com.orangecode.agent.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.orangecode.agent.model.dto.ChatRequest;
import com.orangecode.agent.model.entity.SessionInfo;
import com.orangecode.agent.model.vo.ApiResponse;
import com.orangecode.agent.model.vo.ChatResponse;
import com.orangecode.agent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    @Autowired
    private ChatService chatService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request){
        try{
            logger.info("收到请求：SessionId - {}, Question - {}", request.getId(), request.getQuestion());

            // 参数校验
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            // 获取并创建会话
            SessionInfo session = getOrCreateSession(request.getId());

            // 获取历史信息
            List<Map<String, String>> messageHistory = session.getMessageHistory();

            // 创建API和ChatModel
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            // 记录可用工具类
            chatService.logAvailableTools();

            // 创建系统提示词
            String systemPrompt = chatService.buildSystemPrompt(messageHistory);

            // 组装Agent
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

            // 执行对话
            String answer = chatService.executeChat(agent, request.getQuestion());

            // 更新会话历史
            session.addMessage(request.getQuestion(), answer);
            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}",
                    request.getId(), session.getMessagePairCount());

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(answer)));

        }catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }

    }

    private SessionInfo getOrCreateSession(String sessionId){
        if(sessionId==null || sessionId.isEmpty()){
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, id -> new SessionInfo(id, chatService));
    }
}
