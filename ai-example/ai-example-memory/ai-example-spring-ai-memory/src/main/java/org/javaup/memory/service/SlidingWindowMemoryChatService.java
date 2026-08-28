package org.javaup.memory.service;

import org.javaup.memory.model.MemoryChatResponse;
import org.javaup.memory.support.MemoryPromptSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 滑动窗口记忆服务。
 * <p>
 * 1. 用 {@link MessageWindowChatMemory} 存储最近消息。
 * 2. 用 {@link MessageChatMemoryAdvisor} 在调用前自动把历史塞进 Prompt。
 * 3. 只保留最近 N 轮，不做摘要、不做长期记忆。
 */
@Service
public class SlidingWindowMemoryChatService {

    private static final String DEFAULT_SESSION_ID = "sliding-window-demo";

    private final ChatClient.Builder chatClientBuilder;
    private final ChatMemory chatMemory;
    private final String systemPrompt;

    public SlidingWindowMemoryChatService(
        ChatClient.Builder chatClientBuilder,
        @Value("${app.ai.memory.default-system-prompt}") String systemPrompt,
        @Value("${app.ai.memory.sliding-window.max-rounds:3}") int maxRounds) {
        this.chatClientBuilder = chatClientBuilder;
        this.systemPrompt = systemPrompt;
        // Spring AI 的 MessageWindowChatMemory 按“消息条数”裁剪，不是按“轮数”裁剪。
        // 所以这里把 maxRounds 转成 maxRounds * 2，表示 user + assistant 为一组完整轮次。
        this.chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(Math.max(2, maxRounds * 2))
            .build();
    }

    /**
     * 发起一轮带滑动窗口记忆的对话。
     * <p>
     * 调用过程可以拆成三步：
     * 1. 先根据 sessionId 取出该会话已有的历史消息。
     * 2. 再通过 Advisor 自动把历史消息拼到本轮 Prompt 前面。
     * 3. 调用结束后，Spring AI 会自动把本轮 user / assistant 消息写回 memory。
     */
    public MemoryChatResponse chat(String sessionId, String question) {
        String normalizedSessionId = MemoryPromptSupport.normalizeSessionId(sessionId, DEFAULT_SESSION_ID);
        // 这里先拿“调用前”的历史，是为了估算本轮请求真正发给模型的大致 Token 体积。
        List<Message> historyBeforeCall = this.chatMemory.get(normalizedSessionId);

        String answer = this.chatClientBuilder.build()
            .prompt()
            .system(this.systemPrompt)
            // MessageChatMemoryAdvisor 会在请求前取历史，在响应后把回答追加回去。
            .advisors(MessageChatMemoryAdvisor.builder(this.chatMemory)
                .conversationId(normalizedSessionId)
                .build())
            .user(question)
            .call()
            .content();

        List<Message> historyAfterCall = this.chatMemory.get(normalizedSessionId);
        // 这里只估算“输入”大小，不包含模型输出 Token。
        int promptTokens = MemoryPromptSupport.estimateTokens(this.systemPrompt)
            + MemoryPromptSupport.estimateTokens(question)
            + MemoryPromptSupport.estimateTokens(historyBeforeCall);

        return new MemoryChatResponse(
            "sliding-window",
            normalizedSessionId,
            question,
            answer,
            promptTokens,
            "",
            0,
            MemoryPromptSupport.toViews(historyAfterCall)
        );
    }

    /**
     * 查看某个会话当前窗口里还剩下哪些消息。
     * <p>
     * 演示时这个接口很有用，因为它能直接证明最老的消息已经被窗口挤出去了。
     */
    public MemoryChatResponse snapshot(String sessionId) {
        String normalizedSessionId = MemoryPromptSupport.normalizeSessionId(sessionId, DEFAULT_SESSION_ID);
        List<Message> messages = this.chatMemory.get(normalizedSessionId);
        return new MemoryChatResponse(
            "sliding-window",
            normalizedSessionId,
            "",
            "",
            0,
            "",
            0,
            MemoryPromptSupport.toViews(messages)
        );
    }

    /**
     * 清理某个会话的窗口数据。
     */
    public void clear(String sessionId) {
        this.chatMemory.clear(MemoryPromptSupport.normalizeSessionId(sessionId, DEFAULT_SESSION_ID));
    }

}
