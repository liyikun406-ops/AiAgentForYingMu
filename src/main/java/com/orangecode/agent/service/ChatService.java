package com.orangecode.agent.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.orangecode.agent.tool.DateTimeTools;
import com.orangecode.agent.tool.YuanshenStartTools;
import jakarta.annotation.Resource;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Resource
    private DateTimeTools dateTimeTools;

    @Resource
    private YuanshenStartTools yuanshenStartTools;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Autowired(required = false)  // MCP 禁用时可为 null
    private ToolCallbackProvider tools;


    public DashScopeApi createDashScopeApi(){
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }

    private DashScopeChatModel createDashScopeChatModel(DashScopeApi dashScopeAgentApi, double temperature, int maxToken, double topP){
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeAgentApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createDashScopeChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    // 记录可用工具列表
    public void logAvailableTools(){
        if (tools == null) {
            logger.info("MCP 工具未启用（ToolCallbackProvider 未注入）");
            return;
        }
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        logger.info("可用工具列表:");
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    public String buildSystemPrompt(List<Map<String, String>> history){
        StringBuffer systemPromptBuilder = new StringBuffer();

        if(!history.isEmpty()){
            systemPromptBuilder.append("---对话历史---");
            for(Map<String, String> chat : history){
                String role = chat.get("role");
                String content = chat.get("content");
                if("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");

        return systemPromptBuilder.toString();
    }

    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    private ToolCallback[] getToolCallbacks(){
        if (tools == null) {
            return new ToolCallback[0];  // MCP 禁用时返回空数组
        }
        return tools.getToolCallbacks();
    }
    private Object[] buildMethodToolsArray(){
        return new Object[]{dateTimeTools, yuanshenStartTools};
    }

    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
