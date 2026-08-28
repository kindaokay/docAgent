package org.javaup.route.controller;

import lombok.AllArgsConstructor;
import org.javaup.route.model.RouteChatResponse;
import org.javaup.route.service.SmartRouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * 多路由对话示例接口。
 * 1. 暴露对话入口，方便从浏览器或 curl 直接测试
 * 2. 提供 reset 接口，方便演示多轮对话时清空历史
 */
@AllArgsConstructor
@RestController
@RequestMapping("/rag/route")
public class RouteChatController {

    private final SmartRouteService smartRouteService;

    /**
     * 对外统一入口。
     * sessionId 相同表示同一轮对话线程，系统会自动复用历史消息。
     */
    @GetMapping("/chat")
    public RouteChatResponse chat(
        @RequestParam("question") String question,
        @RequestParam(value = "sessionId", required = false) String sessionId) {
        return smartRouteService.chat(sessionId, question);
    }

    /**
     * 清空指定会话，方便重复演示“第一轮 / 第二轮”效果。
     */
    @GetMapping("/reset")
    public Map<String, Object> reset(@RequestParam(value = "sessionId", required = false) String sessionId) {
        smartRouteService.reset(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId == null ? "route-demo-session" : sessionId);
        result.put("reset", true);
        return result;
    }
}
