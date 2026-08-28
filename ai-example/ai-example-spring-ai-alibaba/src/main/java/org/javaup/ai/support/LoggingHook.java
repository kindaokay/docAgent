package org.javaup.ai.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 支撑组件
 * @author: 阿星不是程序员
 **/
@Component
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class LoggingHook extends AgentHook {

    private static final Logger log = LoggerFactory.getLogger(LoggingHook.class);

    @Override
    public String getName() {
        return "logging_hook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        log.info("ReactAgent start, threadId={}, userMessage={}",
            config.threadId().orElse("default"), extractLastMessage(state, MessageType.USER));
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        log.info("ReactAgent finish, threadId={}, assistantMessage={}",
            config.threadId().orElse("default"), extractLastMessage(state, MessageType.ASSISTANT));
        return CompletableFuture.completedFuture(Map.of());
    }

    private String extractLastMessage(OverAllState state, MessageType targetType) {
        Object messages = state.value("messages").orElse(List.of());
        if (!(messages instanceof List<?> messageList)) {
            return "";
        }
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Object candidate = messageList.get(i);
            if (candidate instanceof AbstractMessage message && message.getMessageType() == targetType) {
                return abbreviate(message.getText());
            }
        }
        return "";
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

}
