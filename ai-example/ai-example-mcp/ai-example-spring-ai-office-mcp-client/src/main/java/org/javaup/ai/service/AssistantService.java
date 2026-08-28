package org.javaup.ai.service;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Service
public class AssistantService {

    private final ChatModel chatModel;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;
    
    private ChatClient chatClient;
    
    public AssistantService(ChatModel chatModel, 
                           SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.chatModel = chatModel;
        this.toolCallbackProvider = toolCallbackProvider;
    }
    
    @PostConstruct
    public void init() {
        // 获取所有MCP工具
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        
        // 构建ChatClient，注入MCP工具
        this.chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbacks)
                .build();
    }
    
    /**
     * 智能助手对话
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}