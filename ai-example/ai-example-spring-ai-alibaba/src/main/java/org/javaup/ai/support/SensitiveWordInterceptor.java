package org.javaup.ai.support;

import java.util.List;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 支撑组件
 * @author: 阿星不是程序员
 **/
@Component
public class SensitiveWordInterceptor extends ModelInterceptor {

    private static final List<String> BLOCKED_WORDS = List.of("炸药", "违法洗钱", "攻击学校");

    private static final String REFUND_GUARD = "额外约束：当用户提到退款、退货、售后时，必须先确认订单号和退款原因，再决定是否调用退款工具。";

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        if (containsBlockedWords(request.getMessages())) {
            return ModelResponse.of(AssistantMessage.builder()
                .content("检测到敏感或违法内容，当前示例不会继续处理该请求。")
                .build());
        }

        ModelRequest decoratedRequest = ModelRequest.builder(request)
            .systemMessage(appendRefundGuard(request.getSystemMessage()))
            .build();
        return handler.call(decoratedRequest);
    }

    @Override
    public String getName() {
        return "sensitive_word_interceptor";
    }

    private boolean containsBlockedWords(List<Message> messages) {
        for (Message message : messages) {
            if (message instanceof AbstractMessage abstractMessage) {
                String text = abstractMessage.getText();
                for (String blockedWord : BLOCKED_WORDS) {
                    if (StringUtils.hasText(text) && text.contains(blockedWord)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private SystemMessage appendRefundGuard(SystemMessage originalMessage) {
        String baseMessage = originalMessage != null && StringUtils.hasText(originalMessage.getText())
            ? originalMessage.getText()
            : "你是一名谨慎的订单助手。";
        return SystemMessage.builder()
            .text(baseMessage + "\n" + REFUND_GUARD)
            .build();
    }

}
