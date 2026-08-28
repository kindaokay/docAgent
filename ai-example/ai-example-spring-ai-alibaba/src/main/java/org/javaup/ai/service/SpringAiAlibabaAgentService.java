package org.javaup.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

import org.javaup.ai.model.OrderSummary;
import org.javaup.ai.support.LoggingHook;
import org.javaup.ai.support.ResettableMemorySaver;
import org.javaup.ai.support.SensitiveWordInterceptor;
import org.javaup.ai.tool.OrderTools;
import org.javaup.ai.tool.SessionContextRequest;
import org.javaup.ai.tool.SessionContextTool;
import org.javaup.ai.tool.ShippingPolicyRequest;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Service
public class SpringAiAlibabaAgentService {

    private static final String DEFAULT_SESSION_ID = "demo-user-1001";

    private static final String SIMPLE_AGENT_PROMPT = """
        你是 JavaUp 的 Spring AI Alibaba 入门助手。
        你的任务是用简洁、结构化的方式回答用户关于 ReactAgent、Tool、Memory 和 Hook 的问题。
        如果用户没有给出上下文，优先给出可以马上动手验证的建议。
        """;

    private static final String ORDER_ASSISTANT_PROMPT = """
        你是 JavaUp 电商订单助手，需要结合工具结果回答问题。
        你可以：
        1. 查询订单状态和物流信息。
        2. 查询商品详情和售后规则。
        3. 处理退款申请，但涉及退款时必须先确认订单号和退款原因。

        回复要求：
        - 优先使用工具返回的事实，不要编造订单信息。
        - 回答要简洁，适合直接返回给前端页面展示。
        - 如果用户追问“这个会话记住了什么”，可以调用 session_context_snapshot 工具总结当前线程上下文。
        """;

    private static final String STRUCTURED_OUTPUT_PROMPT = """
        你是订单结构化摘要助手。
        当用户提到订单号时，请先使用工具查询真实订单信息，然后严格返回 JSON 对象，不要输出 Markdown 代码块。
        JSON 字段固定为：
        {
          "orderId": "...",
          "status": "...",
          "canRefund": true,
          "nextAction": "..."
        }
        nextAction 用一句中文建议说明用户下一步该怎么做。
        """;

    private final ObjectMapper objectMapper;
    private final ReactAgent simpleAgent;
    private final ReactAgent orderAssistantAgent;
    private final ReactAgent orderSummaryAgent;
    private final ResettableMemorySaver orderAssistantMemorySaver;

    public SpringAiAlibabaAgentService(ObjectMapper objectMapper,
                                       ChatModel chatModel,
                                       OrderTools orderTools,
                                       LoggingHook loggingHook,
                                       SensitiveWordInterceptor sensitiveWordInterceptor) {
        this.objectMapper = objectMapper;

        this.orderAssistantMemorySaver = new ResettableMemorySaver();
        ToolCallback shippingPolicyTool = buildShippingPolicyTool();
        ToolCallback sessionContextTool = buildSessionContextTool();

        this.simpleAgent = ReactAgent.builder()
            .name("simple_agent")
            .model(chatModel)
            .systemPrompt(SIMPLE_AGENT_PROMPT)
            .build();

        this.orderAssistantAgent = ReactAgent.builder()
            .name("order_assistant")
            .model(chatModel)
            .methodTools(orderTools)
            .tools(shippingPolicyTool, sessionContextTool)
            .instruction(ORDER_ASSISTANT_PROMPT)
            .saver(this.orderAssistantMemorySaver)
            .hooks(loggingHook)
            .interceptors(sensitiveWordInterceptor)
            .build();

        this.orderSummaryAgent = ReactAgent.builder()
            .name("order_summary_agent")
            .model(chatModel)
            .methodTools(orderTools)
            .instruction(STRUCTURED_OUTPUT_PROMPT)
            .outputType(OrderSummary.class)
            .interceptors(sensitiveWordInterceptor)
            .build();
    }

    public String simpleReply(String question) {
        try {
            return this.simpleAgent.call(question).getText();
        }
        catch (GraphRunnerException exception) {
            throw new IllegalStateException("simple_agent 调用失败", exception);
        }
    }

    public String orderChat(String question, String sessionId) {
        try {
            return this.orderAssistantAgent.call(question, buildSessionConfig(sessionId)).getText();
        }
        catch (GraphRunnerException exception) {
            throw new IllegalStateException("order_assistant 调用失败", exception);
        }
    }

    public Flux<String> streamOrderChat(String question, String sessionId) {
        try {
            return this.orderAssistantAgent.stream(question, buildSessionConfig(sessionId))
                .map(this::extractChunk)
                .filter(StringUtils::hasText);
        }
        catch (GraphRunnerException exception) {
            return Flux.error(new IllegalStateException("order_assistant 流式调用失败", exception));
        }
    }

    public OrderSummary summarizeOrder(String question) {
        try {
            String rawResponse = this.orderSummaryAgent.call(question).getText();
            String jsonPayload = extractJsonPayload(rawResponse);
            try {
                return this.objectMapper.readValue(jsonPayload, OrderSummary.class);
            }
            catch (JsonProcessingException exception) {
                return fallbackSummary(rawResponse);
            }
        }
        catch (GraphRunnerException exception) {
            throw new IllegalStateException("order_summary_agent 调用失败", exception);
        }
    }

    private OrderSummary fallbackSummary(String rawResponse) {
        return new OrderSummary("UNKNOWN", "MODEL_OUTPUT_NOT_JSON", false,
            "模型原始输出：" + rawResponse);
    }

    public Map<String, Object> describeThreadState(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        RunnableConfig runnableConfig = buildSessionConfig(normalizedSessionId);
        Map<String, Object> state = this.orderAssistantMemorySaver.get(runnableConfig)
            .map(Checkpoint::getState)
            .orElseGet(() -> this.orderAssistantAgent.getThreadState(normalizedSessionId));
        if (state == null) {
            state = Map.of();
        }
        Object messages = state.getOrDefault("messages", List.of());
        List<?> messageList = messages instanceof List<?> list ? list : List.of();
        int checkpointCount = this.orderAssistantMemorySaver.list(runnableConfig).size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threadId", normalizedSessionId);
        result.put("checkpointCount", checkpointCount);
        result.put("messageCount", messageList.size());
        result.put("latestUserMessage", findLatestMessage(messageList, MessageType.USER));
        result.put("latestAssistantMessage", findLatestMessage(messageList, MessageType.ASSISTANT));
        return result;
    }

    public Map<String, Object> resetOrderAssistantThread(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        int removedCheckpointCount = this.orderAssistantMemorySaver.clearThread(normalizedSessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threadId", normalizedSessionId);
        result.put("reset", true);
        result.put("removedCheckpointCount", removedCheckpointCount);
        result.put("checkpointCount", 0);
        result.put("messageCount", 0);
        result.put("latestUserMessage", "");
        result.put("latestAssistantMessage", "");
        return result;
    }

    private RunnableConfig buildSessionConfig(String sessionId) {
        return RunnableConfig.builder()
            .threadId(normalizeSessionId(sessionId))
            .build();
    }

    private String normalizeSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId.trim() : DEFAULT_SESSION_ID;
    }

    private ToolCallback buildShippingPolicyTool() {
        return FunctionToolCallback.builder("shipping_policy_lookup", this::shippingPolicyLookup)
            .description("根据商品名称查询发货和售后规则")
            .inputType(ShippingPolicyRequest.class)
            .build();
    }

    private ToolCallback buildSessionContextTool() {
        return FunctionToolCallback.builder("session_context_snapshot", new SessionContextTool())
            .description("读取当前线程上下文，告诉用户会话已经记录了多少历史消息")
            .inputType(SessionContextRequest.class)
            .build();
    }

    private String shippingPolicyLookup(ShippingPolicyRequest request) {
        String productQuery = request != null ? request.productQuery() : "";
        if (StringUtils.hasText(productQuery) && productQuery.contains("耳机")) {
            return "蓝牙耳机 Pro 支持 48 小时内发货，未拆封支持 7 天无理由退货。";
        }
        return "默认规则：24 小时内出库，签收前可拦截，签收后请结合订单状态判断是否可退款。";
    }

    private String extractChunk(NodeOutput output) {
        if (output instanceof StreamingOutput<?> streamingOutput && StringUtils.hasText(streamingOutput.chunk())) {
            return streamingOutput.chunk();
        }
        return "";
    }

    private String extractJsonPayload(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return rawResponse;
        }
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            String[] parts = trimmed.split("\n", 2);
            if (parts.length == 2) {
                return parts[1].replaceAll("```$", "").trim();
            }
        }
        return trimmed;
    }

    private String findLatestMessage(List<?> messageList, MessageType targetType) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Object candidate = messageList.get(i);
            if (candidate instanceof AbstractMessage message && message.getMessageType() == targetType) {
                return message.getText();
            }
        }
        return "";
    }

}
