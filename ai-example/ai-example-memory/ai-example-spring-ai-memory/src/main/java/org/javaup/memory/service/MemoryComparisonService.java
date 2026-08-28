package org.javaup.memory.service;

import org.javaup.memory.model.ComparisonTurnResponse;
import org.javaup.memory.model.MemoryChatResponse;
import org.javaup.memory.model.MemoryComparisonResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 三种记忆策略对比服务。
 * <p>
 * 此类是专门演示用的，给演示准备一条固定脚本：
 * 同样的问题列表，同时传递给三种策略，然后把结果整理成一份结构化响应。
 */
@Service
public class MemoryComparisonService {

    private static final String SCRIPT_NAME = "Spring Bean 作用域六轮追问";

    private static final List<String> QUESTIONS = List.of(
        "Spring Bean 的作用域有哪些？",
        "默认用的是哪一种？",
        "那它在并发下会不会有线程安全问题？",
        "如果换成 prototype，还会走完整生命周期回调吗？",
        "那在项目里，我该怎么判断一个组件更适合 singleton 还是 prototype？",
        "回到最开始那个问题，除了常见那几种作用域，还能自己扩展吗？"
    );

    private final NoMemoryChatService noMemoryChatService;
    private final SlidingWindowMemoryChatService slidingWindowMemoryChatService;
    private final SummaryCompressionMemoryChatService summaryCompressionMemoryChatService;

    public MemoryComparisonService(NoMemoryChatService noMemoryChatService,
                                   SlidingWindowMemoryChatService slidingWindowMemoryChatService,
                                   SummaryCompressionMemoryChatService summaryCompressionMemoryChatService) {
        this.noMemoryChatService = noMemoryChatService;
        this.slidingWindowMemoryChatService = slidingWindowMemoryChatService;
        this.summaryCompressionMemoryChatService = summaryCompressionMemoryChatService;
    }

    /**
     * 执行默认对比脚本。
     * <p>
     * 演示时建议直接调用这个方法对应的接口，因为它会自动：
     * 1. 清空旧会话，保证每次结果可复现。
     * 2. 按固定的 6 个问题顺序连续提问。
     * 3. 返回每一轮在三种策略下的回答和摘要状态。
     */
    public MemoryComparisonResponse runDefaultComparison() {
        String slidingSessionId = "compare-sliding-window";
        String summarySessionId = "compare-summary-memory";

        // 每次对比前先重置，保证现场演示不会被前一次数据污染。
        this.slidingWindowMemoryChatService.clear(slidingSessionId);
        this.summaryCompressionMemoryChatService.clear(summarySessionId);

        List<ComparisonTurnResponse> turns = new ArrayList<>();
        int round = 1;
        for (String question : QUESTIONS) {
            MemoryChatResponse noMemoryResponse = this.noMemoryChatService.chat(question);
            MemoryChatResponse slidingWindowResponse = this.slidingWindowMemoryChatService.chat(slidingSessionId,
                question);
            MemoryChatResponse summaryResponse = this.summaryCompressionMemoryChatService.chat(summarySessionId,
                question);

            turns.add(new ComparisonTurnResponse(
                round++,
                question,
                noMemoryResponse.answer(),
                slidingWindowResponse.answer(),
                summaryResponse.answer(),
                summaryResponse.summary(),
                summaryResponse.compressionCount()
            ));
        }

        return new MemoryComparisonResponse(
            SCRIPT_NAME,
            turns,
            this.slidingWindowMemoryChatService.snapshot(slidingSessionId),
            this.summaryCompressionMemoryChatService.snapshot(summarySessionId)
        );
    }

}
