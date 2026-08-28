package org.javaup.memory.controller;

import lombok.AllArgsConstructor;
import org.javaup.memory.model.MemoryChatResponse;
import org.javaup.memory.model.MemoryComparisonResponse;
import org.javaup.memory.service.MemoryComparisonService;
import org.javaup.memory.service.NoMemoryChatService;
import org.javaup.memory.service.SlidingWindowMemoryChatService;
import org.javaup.memory.service.SummaryCompressionMemoryChatService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * 会话记忆演示入口。
 * <p>
 * 这个 Controller 不追求复杂封装，目的是为了演示时能一眼看明白：
 * 1. 无记忆、滑动窗口、摘要压缩分别怎么调用。
 * 2. 如何查看某个会话当前保存了什么内容。
 * 3. 如何一键跑完三种策略的对比脚本。
 */
@AllArgsConstructor
@RestController
@RequestMapping("/memory")
public class MemoryDemoController {

    private final NoMemoryChatService noMemoryChatService;
    private final SlidingWindowMemoryChatService slidingWindowMemoryChatService;
    private final SummaryCompressionMemoryChatService summaryCompressionMemoryChatService;
    private final MemoryComparisonService memoryComparisonService;
    

    /**
     * 无记忆模式。
     * <p>
     * 每次请求只带 system prompt 和当前问题，不带任何历史。
     * 这里故意使用 GET + query param，方便示例项目直接在浏览器或 curl 里调用。
     */
    @GetMapping("/no-memory/chat")
    public MemoryChatResponse noMemoryChat(@RequestParam("question") String question) {
        return this.noMemoryChatService.chat(requireQuestion(question));
    }

    /**
     * 滑动窗口模式。
     * <p>
     * 需要传 question，sessionId 则可选。
     * 如果不传 sessionId，会自动回退到服务内部的默认会话。
     * 这个接口特别适合演示“最近几轮能接上，但太早的历史会被挤掉”。
     */
    @GetMapping("/sliding-window/chat")
    public MemoryChatResponse slidingWindowChat(@RequestParam(value = "sessionId", required = false) String sessionId,
                                                @RequestParam("question") String question) {
        return this.slidingWindowMemoryChatService.chat(sessionId, requireQuestion(question));
    }

    /**
     * 摘要压缩模式。
     * <p>
     * 当最近对话累计到一定长度后，会自动把更早内容压缩成摘要，
     * 然后把摘要和最近几轮完整消息一起送给模型。
     * 这里也使用 GET，目的是让示例更轻，调用时不需要再构造 JSON 请求体。
     */
    @GetMapping("/summary/chat")
    public MemoryChatResponse summaryChat(@RequestParam(value = "sessionId", required = false) String sessionId,
                                          @RequestParam("question") String question) {
        return this.summaryCompressionMemoryChatService.chat(sessionId, requireQuestion(question));
    }

    /**
     * 一次性跑完三种策略的对比脚本。
     * <p>
     * 返回结果里会包含每一轮问题在三种策略下的回答，适合直接做接口演示。
     */
    @GetMapping("/compare")
    public MemoryComparisonResponse compare() {
        return this.memoryComparisonService.runDefaultComparison();
    }

    /**
     * 查看某个会话目前保存下来的记忆内容。
     * <p>
     * 演示时建议在跑完多轮对话后调用一次，
     * 这样能直观看到滑动窗口和摘要压缩到底各自保留了什么。
     */
    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> sessionState(@PathVariable("sessionId") String sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("slidingWindow", this.slidingWindowMemoryChatService.snapshot(sessionId));
        result.put("summaryCompression", this.summaryCompressionMemoryChatService.snapshot(sessionId));
        return result;
    }

    /**
     * 清空指定会话的记忆。
     * <p>
     * 每次重新演示前先调这个接口，可以保证现场结果稳定，不会被上一次的历史干扰。
     */
    @GetMapping("/sessions/delete/{sessionId}")
    public Map<String, Object> reset(@PathVariable("sessionId") String sessionId) {
        this.slidingWindowMemoryChatService.clear(sessionId);
        this.summaryCompressionMemoryChatService.clear(sessionId);
        return Map.of(
            "sessionId", sessionId,
            "reset", true
        );
    }

    /**
     * 统一校验用户问题。
     * 这里故意保持简单，演示项目里没必要再单独抽参数校验器。
     */
    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question 不能为空");
        }
        return question.trim();
    }

}
