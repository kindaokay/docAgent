package org.javaup.ai.controller;

import java.util.Map;

import reactor.core.publisher.Flux;

import org.javaup.ai.model.OrderSummary;
import org.javaup.ai.service.SpringAiAlibabaAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/agent/order")
public class OrderAssistantController {

    private final SpringAiAlibabaAgentService agentService;

    public OrderAssistantController(SpringAiAlibabaAgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/chat")
    public String chat(
        @RequestParam(value = "question", defaultValue = "帮我查一下订单 ORD-1001 的状态") String question,
        @RequestParam(value = "sessionId", defaultValue = "demo-user-1001") String sessionId) {
        return this.agentService.orderChat(question, sessionId);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
        @RequestParam(value = "question", defaultValue = "请先查询订单 ORD-1001，再告诉我下一步建议") String question,
        @RequestParam(value = "sessionId", defaultValue = "demo-user-1001") String sessionId) {
        return this.agentService.streamOrderChat(question, sessionId);
    }

    @GetMapping("/summary")
    public OrderSummary summary(
        @RequestParam(value = "question", defaultValue = "请先查询订单 ORD-1001，再用结构化 JSON 总结订单状态、是否可退款和下一步建议")
        String question) {
        return this.agentService.summarizeOrder(question);
    }

    @GetMapping("/thread-state")
    public Map<String, Object> threadState(
        @RequestParam(value = "sessionId", defaultValue = "demo-user-1001") String sessionId) {
        return this.agentService.describeThreadState(sessionId);
    }

    @GetMapping("/reset-thread")
    public Map<String, Object> resetThread(
        @RequestParam(value = "sessionId", defaultValue = "demo-user-1001") String sessionId) {
        return this.agentService.resetOrderAssistantThread(sessionId);
    }

}
