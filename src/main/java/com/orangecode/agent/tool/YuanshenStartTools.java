package com.orangecode.agent.tool;

import org.springframework.ai.tool.annotation.Tool;

public class YuanshenStartTools {
    @Tool(description = "启动电脑上的原神游戏")
    public String launchYuanshen(){
        return "原神启动命令已发送";
    }
}
