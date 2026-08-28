package org.javaup.memory.service;

import org.javaup.memory.model.MemoryChatResponse;
import org.javaup.memory.support.MemoryPromptSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 无记忆对话服务。
 * <p>
 * 每次请求都只发 system prompt 和当前用户问题，让大家看到没有记忆时模型会怎样“断片”。
 */
@Service
public class NoMemoryChatService {

    private static final String STATELESS_SESSION_ID = "stateless-demo";

    private final ChatClient chatClient;
    private final String systemPrompt;

    public NoMemoryChatService(ChatClient.Builder chatClientBuilder,
                               @Value("${app.ai.memory.default-system-prompt}") String systemPrompt) {
        this.chatClient = chatClientBuilder.build();
        this.systemPrompt = systemPrompt;
    }

    /**
     * 单轮问答。
     * <p>
     * 不接收 sessionId，也不读取历史，所以同一个用户连问两次，
     * 模型也不会知道第二次里的“它”“刚才那个”具体指什么。
     */
    public MemoryChatResponse chat(String question) {
        String answer = this.chatClient.prompt()
            .system(this.systemPrompt)
            .user(question)
            .call()
            .content();
        // 这里只做一个非常粗略的 Token 估算，方便演示时观察不同策略的输入体量变化。
        int promptTokens = MemoryPromptSupport.estimateTokens(this.systemPrompt)
            + MemoryPromptSupport.estimateTokens(question);
        return new MemoryChatResponse(
            "no-memory",
            STATELESS_SESSION_ID,
            question,
            answer,
            promptTokens,
            "",
            0,
            List.of()
        );
    }

}
