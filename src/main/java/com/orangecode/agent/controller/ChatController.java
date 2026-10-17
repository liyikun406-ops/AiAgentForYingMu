package com.orangecode.agent.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.orangecode.agent.model.dto.ChatRequest;
import com.orangecode.agent.model.dto.ClearRequest;
import com.orangecode.agent.model.entity.SessionInfo;
import com.orangecode.agent.response.ApiResponse;
import com.orangecode.agent.response.ChatResponse;
import com.orangecode.agent.response.SessionInfoResponse;
import com.orangecode.agent.response.SseResponse;
import com.orangecode.agent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

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

    /**
     * 清空会话历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request){
        try{
            String id = request.getId();
            if(id == null || id.isEmpty()){
                return ResponseEntity.ok(ApiResponse.error("会话Id不能为空"));
            }

            SessionInfo sessionInfo = sessions.get(id);
            if(sessionInfo != null ){
                sessionInfo.clearHistory();
                return ResponseEntity.ok(ApiResponse.success("清空会话成功"));
            }
            else{
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
        } catch (Exception e){
            logger.error("清空对话失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ReactAgent 对话接口（SSE 流式模式，支持多轮对话，支持自动工具调用，例如获取当前时间，查询日志，告警等）
     * 支持 session 管理，保留对话历史
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request){
        SseEmitter emitter = new SseEmitter(300000L);

        if(request.getQuestion() == null || request.getQuestion().isEmpty()){
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message").data(SseResponse.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executor.execute(() -> {
            try{
                logger.info("收到ReactAgent 对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

                // 获取或创建会话
                SessionInfo session = getOrCreateSession(request.getId());

                // 获取历史消息
                List<Map<String, String>> history = session.getMessageHistory();
                logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);

                // 创建 DashScope API 和 ChatModel
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                // 记录可用工具
                chatService.logAvailableTools();

                // 构建系统提示词（包含历史消息）
                String systemPrompt = chatService.buildSystemPrompt(history);

                // 创建 ReactAgent
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

                // 用于累积完整答案
                StringBuilder fullAnswerBuilder = new StringBuilder();

                // 使用 agent.stream() 进行流式对话
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());

                stream.subscribe(
                        output -> {
                            try{
                                if(output instanceof StreamingOutput){
                                    StreamingOutput streamingOutput = (StreamingOutput) output;
                                    OutputType type = streamingOutput.getOutputType();

                                    // 处理模型推理的流式输出
                                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                                        // 流式增量内容，逐步显示
                                        String chunk = streamingOutput.message().getText();
                                        if (chunk != null && !chunk.isEmpty()) {
                                            fullAnswerBuilder.append(chunk);

                                            // 实时发送到前端
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(SseResponse.content(chunk), MediaType.APPLICATION_JSON));

                                            logger.debug("发送流式内容: {}", chunk);
                                        }
                                    } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                        // 模型推理完成
                                        logger.debug("模型输出完成");
                                    } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                        // 工具调用完成
                                        logger.debug("工具调用完成: {}", output.node());
                                    } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                        // Hook 执行完成
                                        logger.debug("Hook 执行完成: {}", output.node());
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        error -> {
                            // 错误处理
                            logger.error("ReactAgent 流式对话失败", error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(SseResponse.error(error.getMessage()), MediaType.APPLICATION_JSON));
                            } catch (IOException ex) {
                                logger.error("发送错误消息失败", ex);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            // 完成处理
                            try {
                                String fullAnswer = fullAnswerBuilder.toString();
                                logger.debug("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}",
                                        request.getId(), fullAnswer.length());

                                // 更新会话历史
                                session.addMessage(request.getQuestion(), fullAnswer);
                                logger.debug("已更新会话历史 - SessionId: {}, 当前消息对数: {}",
                                        request.getId(), session.getMessagePairCount());

                                // 发送完成标记
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(SseResponse.done(), MediaType.APPLICATION_JSON));
                                emitter.complete();
                            } catch (IOException e) {
                                logger.error("发送完成消息失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e){
                logger.error("ReactAgent 对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(SseResponse.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;

    }

    /**
     * AI 智能运维接口（SSE 流式模式）- 自动分析告警并生成运维报告
     * 无需用户输入，自动执行告警分析流程
     */


    /**
     * 获取会话信息
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId){
        SessionInfoResponse response = new SessionInfoResponse();
        if(sessionId != null){
            SessionInfo sessionInfo = sessions.get(sessionId);
            if(sessionInfo == null){
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
            else{
                response.setSessionId(sessionId);
                response.setCreateTime(sessionInfo.getCreateTime());
                response.setMessagePairCOunt(sessionInfo.getMessagePairCount());
                return ResponseEntity.ok(ApiResponse.success(response));
            }
        }

        return ResponseEntity.ok(ApiResponse.error("会话Id不能为空"));
    }

    private SessionInfo getOrCreateSession(String sessionId){
        if(sessionId==null || sessionId.isEmpty()){
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, id -> new SessionInfo(id, chatService));
    }
}
