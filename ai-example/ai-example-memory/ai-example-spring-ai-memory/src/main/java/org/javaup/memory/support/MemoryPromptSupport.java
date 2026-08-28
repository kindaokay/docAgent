package org.javaup.memory.support;

import java.util.ArrayList;
import java.util.List;

import org.javaup.memory.model.ConversationMessageView;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 支撑组件
 * @author: 阿星不是程序员
 **/
public final class MemoryPromptSupport {

    private MemoryPromptSupport() {
    }

    /**
     * 规范化 sessionId。
     * <p>
     * 演示接口里很多地方都允许 sessionId 为空，这时会回退到默认值，
     * 方便用户不传参也能直接体验单条固定会话。
     */
    public static String normalizeSessionId(String sessionId, String defaultSessionId) {
        return StringUtils.hasText(sessionId) ? sessionId.trim() : defaultSessionId;
    }

    /**
     * 从 Spring AI 的 ChatResponse 里安全取出文本内容。
     */
    public static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 估算一组消息大概会占用多少输入 Token。
     * <p>
     * 注意这里不是精确值，只是为了在示例项目中观察不同策略的大致输入体量。
     */
    public static int estimateTokens(List<Message> messages) {
        return messages.stream()
            .mapToInt(message -> estimateTokens(extractMessageText(message)))
            .sum();
    }

    /**
     * 中文和非中文字符采用非常粗粒度的换算方式。
     * <p>
     * 真正生产环境里如果要精确计费，建议接入模型对应 tokenizer；
     * 这里为了演示方便，只保留轻量逻辑。
     */
    public static int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char current : text.toCharArray()) {
            if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                chineseChars++;
            }
            else if (!Character.isWhitespace(current)) {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars * 1.5 + otherChars / 4.0);
    }

    /**
     * 把 Spring AI Message 转成更适合接口返回的视图对象。
     */
    public static List<ConversationMessageView> toViews(List<Message> messages) {
        List<ConversationMessageView> result = new ArrayList<>(messages.size());
        for (Message message : messages) {
            result.add(new ConversationMessageView(resolveRole(message), extractMessageText(message)));
        }
        return result;
    }

    /**
     * 把消息列表转成“角色：内容”的纯文本，方便拿去做摘要 Prompt。
     */
    public static String toTranscript(List<Message> messages) {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messages) {
            transcript.append(resolveRole(message))
                .append("：")
                .append(extractMessageText(message))
                .append('\n');
        }
        return transcript.toString();
    }

    /**
     * 安全提取消息正文。
     */
    public static String extractMessageText(Message message) {
        if (message instanceof AbstractMessage abstractMessage) {
            return abstractMessage.getText();
        }
        return String.valueOf(message);
    }

    /**
     * 把 Spring AI 的消息类型转换成更直观的角色名。
     */
    private static String resolveRole(Message message) {
        return switch (message.getMessageType()) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

}
