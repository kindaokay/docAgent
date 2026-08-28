package org.javaup.ai.tool;

import java.util.List;
import java.util.function.BiFunction;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.StringUtils;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 工具类
 * @author: 阿星不是程序员
 **/
public class SessionContextTool implements BiFunction<SessionContextRequest, ToolContext, String> {

    @Override
    public String apply(SessionContextRequest request, ToolContext toolContext) {
        Object stateObject = toolContext.getContext().get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
        Object configObject = toolContext.getContext().get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);

        int messageCount = 0;
        if (stateObject instanceof OverAllState state) {
            Object messages = state.value("messages").orElse(List.of());
            if (messages instanceof List<?> messageList) {
                messageCount = messageList.size();
            }
        }

        String threadId = configObject instanceof RunnableConfig config
            ? config.threadId().orElse("unknown")
            : "unknown";
        String focus = request != null ? request.focus() : "";
        String focusText = StringUtils.hasText(focus) ? focus.trim() : "当前对话";

        return "当前线程 " + threadId + " 已累计 " + messageCount + " 条消息，重点关注：" + focusText + "。";
    }

}
