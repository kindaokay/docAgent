package org.javaup.memory.service;

import org.javaup.memory.model.MemoryChatResponse;
import org.javaup.memory.support.MemoryPromptSupport;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 摘要压缩记忆服务。
 * <p>
 * 1. 最近几轮对话保留原文，保证模型能准确理解当前追问。
 * 2. 更早的历史不直接丢掉，而是压缩成摘要。
 * 3. 下一轮请求时，把“摘要 + 最近几轮”一起发给模型。
 * <p>
 * 它不是要替代 Spring AI 的 ChatMemory，而是演示当内置窗口不够用时，
 * 我们如何在 Spring AI 的 Message / Prompt / ChatModel 之上添加一层简单编排逻辑。
 */
@Service
public class SummaryCompressionMemoryChatService {

    private static final String DEFAULT_SESSION_ID = "summary-memory-demo";

    private static final String SUMMARY_SYSTEM_PROMPT = """
        你是一个会话压缩助手。
        你的任务不是回答业务问题，而是把历史对话整理成下一轮还能继续接话的背景摘要。
        重点保留：
        1. 用户真正关心的主题和目标。
        2. 关键技术细节、版本、报错和结论。
        3. 已经排除的方案，以及还没解决的问题。
        忽略寒暄、重复确认和无关闲聊。
        直接输出摘要正文，不要加标题，不要输出 Markdown。
        """;

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final int tokenThreshold;
    private final int keepRecentRounds;
    private final Map<String, SummaryConversationState> sessionStore = new ConcurrentHashMap<>();

    public SummaryCompressionMemoryChatService(
        ChatModel chatModel,
        @Value("${app.ai.memory.default-system-prompt}") String systemPrompt,
        @Value("${app.ai.memory.summary.token-threshold:700}") int tokenThreshold,
        @Value("${app.ai.memory.summary.keep-recent-rounds:2}") int keepRecentRounds) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
        this.tokenThreshold = tokenThreshold;
        this.keepRecentRounds = keepRecentRounds;
    }

    /**
     * 发起一轮带摘要压缩记忆的对话。
     * <p>
     * 整体流程如下：
     * 1. 先把 system prompt、历史摘要、最近几轮原始对话、本轮问题组装成 Prompt。
     * 2. 调模型拿到回答后，把本轮 user / assistant 原文先写入 recentMessages。
     * 3. 如果 recentMessages 太长，就把更早的部分压缩成新的 summary。
     */
    public MemoryChatResponse chat(String sessionId, String question) {
        String normalizedSessionId = MemoryPromptSupport.normalizeSessionId(sessionId, DEFAULT_SESSION_ID);
        SummaryConversationState state = this.sessionStore.computeIfAbsent(normalizedSessionId,
            key -> new SummaryConversationState());

        // 同一个 sessionId 下可能被连续调用，所以这里对单个会话状态做串行保护，
        // 避免摘要和 recentMessages 在并发演示时互相覆盖。
        synchronized (state) {
            List<Message> promptMessages = buildPromptMessages(state, question);
            String answer = MemoryPromptSupport.extractText(this.chatModel.call(new Prompt(promptMessages)));

            // 先保留原始问答，后面再根据阈值判断是否需要压缩。
            state.recentMessages.add(new UserMessage(question));
            state.recentMessages.add(new AssistantMessage(answer));
            compressIfNecessary(state);

            return new MemoryChatResponse(
                "summary-compression",
                normalizedSessionId,
                question,
                answer,
                MemoryPromptSupport.estimateTokens(promptMessages),
                state.summary,
                state.compressionCount,
                MemoryPromptSupport.toViews(state.recentMessages)
            );
        }
    }

    /**
     * 查看某个会话当前的摘要和最近保留消息。
     */
    public MemoryChatResponse snapshot(String sessionId) {
        String normalizedSessionId = MemoryPromptSupport.normalizeSessionId(sessionId, DEFAULT_SESSION_ID);
        SummaryConversationState state = this.sessionStore.get(normalizedSessionId);
        if (state == null) {
            return new MemoryChatResponse("summary-compression", normalizedSessionId, "", "", 0, "", 0, List.of());
        }
        synchronized (state) {
            return new MemoryChatResponse(
                "summary-compression",
                normalizedSessionId,
                "",
                "",
                0,
                state.summary,
                state.compressionCount,
                MemoryPromptSupport.toViews(state.recentMessages)
            );
        }
    }

    /**
     * 清空指定会话的摘要和最近消息。
     */
    public void clear(String sessionId) {
        this.sessionStore.remove(MemoryPromptSupport.normalizeSessionId(sessionId, DEFAULT_SESSION_ID));
    }

    /**
     * 组装本轮真正送给模型的消息列表。
     * <p>
     * 顺序非常关键：
     * 1. system prompt 先告诉模型它是谁、应该怎么回答。
     * 2. 如果有摘要，作为额外 system message 注入，给模型补上长期背景。
     * 3. recentMessages 保留最近几轮完整细节。
     * 4. 最后追加当前用户问题。
     */
    private List<Message> buildPromptMessages(SummaryConversationState state, String question) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(this.systemPrompt));
        if (!state.summary.isBlank()) {
            messages.add(new SystemMessage("【历史摘要】" + state.summary));
        }
        messages.addAll(state.recentMessages);
        messages.add(new UserMessage(question));
        return messages;
    }

    /**
     * 判断是否需要把更早的对话压缩成摘要。
     * <p>
     * 这里用的是“粗略 Token 估算”而不是精确 tokenizer，原因很简单：
     * 这是演示项目，重点是把策略行为展示清楚，不需要为了精确计数把示例写复杂。
     */
    private void compressIfNecessary(SummaryConversationState state) {
        if (MemoryPromptSupport.estimateTokens(state.recentMessages) <= this.tokenThreshold) {
            return;
        }

        // keepRecentRounds 代表要保留多少轮完整问答，所以这里同样按 2 倍消息数换算。
        int keepCount = Math.max(2, this.keepRecentRounds * 2);
        if (state.recentMessages.size() <= keepCount) {
            return;
        }

        // overflowMessages 是“要被压缩成摘要”的那一段较早历史。
        List<Message> overflowMessages = new ArrayList<>(
            state.recentMessages.subList(0, state.recentMessages.size() - keepCount)
        );
        // recentMessages 则是继续保留原文的最近几轮内容。
        List<Message> recentMessages = new ArrayList<>(
            state.recentMessages.subList(state.recentMessages.size() - keepCount, state.recentMessages.size())
        );

        state.summary = mergeSummary(state.summary, overflowMessages);
        state.recentMessages.clear();
        state.recentMessages.addAll(recentMessages);
        state.compressionCount++;
    }

    /**
     * 把已有摘要和新溢出的历史对话合并成一个新的摘要。
     * <p>
     * 这样做的好处是：摘要不会无限增长，而是每次都重新折叠成一段更紧凑的背景说明。
     */
    private String mergeSummary(String existingSummary, List<Message> overflowMessages) {
        String summaryPrompt = """
            请把下面的已有摘要和新增对话合并成一段新的背景摘要。
            输出要求：
            1. 直接输出摘要正文，不要加标题。
            2. 保留用户当前关注的主题、关键技术点、已经确认的结论和待解决问题。
            3. 合并重复内容，别把对话原文逐句照搬。
            4. 控制在 180 到 220 字之间。

            已有摘要：
            %s

            新增对话：
            %s
            """.formatted(
            existingSummary.isBlank() ? "暂无" : existingSummary,
            MemoryPromptSupport.toTranscript(overflowMessages)
        );

        List<Message> summaryMessages = List.of(
            new SystemMessage(SUMMARY_SYSTEM_PROMPT),
            new UserMessage(summaryPrompt)
        );
        return MemoryPromptSupport.extractText(this.chatModel.call(new Prompt(summaryMessages)));
    }

    /**
     * 单个会话的内存状态。
     * <p>
     * summary 负责保留长期背景，recentMessages 负责保留短期细节。
     * compressionCount 主要是给演示用的，方便观察摘要到底触发了几次。
     */
    private static final class SummaryConversationState {

        private String summary = "";
        private int compressionCount = 0;
        private final List<Message> recentMessages = new ArrayList<>();

    }

}
