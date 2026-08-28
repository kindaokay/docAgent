package org.javaup.route.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.route.model.RouteIntent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 示例版意图分类器。
 * 先用几条简单规则做快速判断，规则拦不住再交给大模型。
 */
@Slf4j
@Service
public class RouteIntentClassifier {

    private static final Set<String> GREETING_WORDS = Set.of(
        "你好", "您好", "hi", "hello", "在吗", "谢谢", "感谢", "拜拜", "再见"
    );

    private static final Set<String> TOOL_WORDS = Set.of(
        "订单", "订单号", "支付", "进度", "学到哪", "直播", "排期", "班级"
    );

    private static final Set<String> AMBIGUOUS_WORDS = Set.of(
        "推荐", "怎么弄", "怎么办", "这个呢", "那个呢", "课程"
    );

    private static final String INTENT_PROMPT = """
        你是一个路由分类助手。
        请根据历史对话和当前问题，只返回下面四个词里的一个：
        knowledge
        tool
        chitchat
        clarify

        历史对话：
        {history}

        当前问题：
        {question}
        """;

    private final ChatClient chatClient;

    public RouteIntentClassifier(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 对外只有一个入口，示例里尽量保持简单。
     */
    public RouteIntent classify(String question, String historyText) {
        RouteIntent ruleIntent = classifyByRule(question, historyText);
        if (ruleIntent != null) {
            return ruleIntent;
        }
        return classifyByLlm(question, historyText);
    }

    /**
     * 规则层只拦最明显的情况，避免示例写得太复杂。
     */
    private RouteIntent classifyByRule(String question, String historyText) {
        String normalized = normalize(question);
        if (!StringUtils.hasText(normalized)) {
            return RouteIntent.CLARIFY;
        }

        if (GREETING_WORDS.stream().anyMatch(normalized::contains)) {
            return RouteIntent.CHITCHAT;
        }

        if (hasToolWord(normalized) || hasOrderId(normalized)) {
            return RouteIntent.TOOL;
        }

        if ((normalized.contains("继续") || normalized.contains("好的")) && historyText.contains("订单")) {
            return RouteIntent.TOOL;
        }

        if (normalized.length() <= 4 || AMBIGUOUS_WORDS.contains(normalized)) {
            return RouteIntent.CLARIFY;
        }

        return null;
    }

    /**
     * 规则判断不了，再让模型做语义判断。
     * 为了示例直观，这里只让模型返回一个单词，不做复杂验证。
     */
    private RouteIntent classifyByLlm(String question, String historyText) {
        try {
            String content = chatClient.prompt()
                .user(user -> user.text(INTENT_PROMPT)
                    .param("history", StringUtils.hasText(historyText) ? historyText : "无历史对话")
                    .param("question", question))
                .call()
                .content();
            return RouteIntent.from(cleanIntent(content));
        }
        catch (Exception exception) {
            log.warn("意图识别失败，默认回到知识检索: {}", exception.getMessage());
            return RouteIntent.KNOWLEDGE;
        }
    }

    private boolean hasToolWord(String normalized) {
        return TOOL_WORDS.stream().anyMatch(normalized::contains);
    }

    private boolean hasOrderId(String normalized) {
        return normalized.matches(".*ju[-_]?\\d{8}[-_]?\\d{4}.*");
    }

    private String cleanIntent(String content) {
        if (!StringUtils.hasText(content)) {
            return "knowledge";
        }
        String cleaned = content.trim().toLowerCase(Locale.ROOT);
        if (cleaned.contains("tool")) {
            return "tool";
        }
        if (cleaned.contains("chitchat")) {
            return "chitchat";
        }
        if (cleaned.contains("clarify")) {
            return "clarify";
        }
        if (cleaned.contains("knowledge")) {
            return "knowledge";
        }
        return "knowledge";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
