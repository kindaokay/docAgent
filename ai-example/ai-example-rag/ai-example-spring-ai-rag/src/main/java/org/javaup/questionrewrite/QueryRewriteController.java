package org.javaup.questionrewrite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * 问题改写演示 Controller —— 五种改写策略的完整示例
 * <p>
 * 每个接口都内置了模拟对话数据，配置好 apiKey 后直接调用即可测试。
 * <p>
 * 测试地址（端口默认7092）：
 * <ul>
 *   <li>自定义改写：    GET /rag/rewrite/custom?question=那它有没有证书</li>
 *   <li>Compression：  GET /rag/rewrite/compression?question=那它有没有证书</li>
 *   <li>Rewriter：     GET /rag/rewrite/rewriter?question=ES查询太慢了怎么搞</li>
 *   <li>Expand：       GET /rag/rewrite/expand?question=Redis持久化方式有哪些</li>
 *   <li>HyDE：        GET /rag/rewrite/hyde?question=微服务之间怎么通信</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/rag/rewrite")
public class QueryRewriteController {

    private final ChatClient chatClient;
    private final ChatClient.Builder chatClientBuilder;
    private final QueryRewriteService queryRewriteService;

    /**
     * 模拟的对话历史 —— 演示指代消解和上下文补全
     * <p>
     * 场景：用户先问了"Python入门课多少钱"，助手回答了价格，
     * 然后用户接着问"那它有没有证书"——这个"它"指的是Python入门课。
     * 人一看就懂，但向量检索拿到的是"那它有没有证书"这几个字，会完全跑偏。
     */
    private static final List<Message> MOCK_HISTORY = List.of(
            new UserMessage("Python入门课多少钱？"),
            new AssistantMessage("Python入门课目前售价299元，包含60课时的视频教程和3个实战项目。")
    );

    public QueryRewriteController(ChatClient.Builder chatClientBuilder,
                                  QueryRewriteService queryRewriteService) {
        this.chatClientBuilder = chatClientBuilder;
        this.chatClient = chatClientBuilder.build();
        this.queryRewriteService = queryRewriteService;
    }

    // ==================== 方式一：自定义改写服务 ====================

    /**
     * 自定义改写服务 —— 最灵活的方式，完全自己控制改写逻辑
     * <p>
     * 特点：
     * - 启发式前置判断：先用简单规则判断是否需要改写，省掉30-40%的无效LLM调用
     * - 安全兜底：LLM调用失败时回退到原始问题
     * - 结果缓存：同一session内相同问题不重复调用LLM
     * <p>
     * 测试：GET /rag/rewrite/custom?question=那它有没有证书
     * 预期改写：Python入门课是否提供结业证书？
     */
    @GetMapping("/custom")
    public Map<String, Object> custom(@RequestParam("question") String question) {
        long start = System.currentTimeMillis();
        // safeRewrite 内部会做：启发式判断 → LLM改写 → 结果校验 → 异常兜底
        String rewritten = queryRewriteService.safeRewrite(question, MOCK_HISTORY);
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("original", question);
        result.put("rewritten", rewritten);
        result.put("history", formatHistory(MOCK_HISTORY));
        result.put("latencyMs", latency);
        return result;
    }

    // ==================== 方式二：CompressionQueryTransformer ====================

    /**
     * Spring AI 内置 CompressionQueryTransformer —— 处理多轮对话中的指代和省略
     * <p>
     * 它会把对话历史压缩进查询，生成一个独立的检索语句。
     * 适合多轮对话场景，开箱即用，不需要自己写Prompt。
     * <p>
     * 注意：Query构造需要三个参数（text, history, context），
     * Spring AI 1.1.0 没有两参数的构造函数，context传 Collections.emptyMap() 即可。
     * <p>
     * 测试：GET /rag/rewrite/compression?question=那它有没有证书
     * 预期改写：Python入门课是否提供结业证书？
     */
    @GetMapping("/compression")
    public Map<String, Object> compression(@RequestParam("question") String question) {
        // 构建 CompressionQueryTransformer，只需要传入 chatClientBuilder
        CompressionQueryTransformer compression = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        // 构造带对话历史的 Query（三参数：问题文本、历史消息、上下文Map）
        Query query = new Query(question, MOCK_HISTORY, Collections.emptyMap());
        long start = System.currentTimeMillis();
        // transform 会调用LLM，把对话历史和当前问题压缩成一个独立查询
        Query rewritten = compression.transform(query);
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("original", question);
        result.put("rewritten", rewritten.text());
        result.put("history", formatHistory(MOCK_HISTORY));
        result.put("latencyMs", latency);
        return result;
    }

    // ==================== 方式三：RewriteQueryTransformer ====================

    /**
     * Spring AI 内置 RewriteQueryTransformer —— 优化查询表达（口语转书面）
     * <p>
     * 注意：它不能处理指代消解，只做表达优化。
     * 适合单轮对话场景，把口语化的表达转成知识库更可能使用的书面表达。
     * <p>
     * 测试：GET /rag/rewrite/rewriter?question=ES查询太慢了怎么搞
     * 预期改写：Elasticsearch查询性能优化方案
     */
    @GetMapping("/rewriter")
    public Map<String, Object> rewriter(@RequestParam("question") String question) {
        RewriteQueryTransformer rewriter = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        // 单参数构造，不需要对话历史
        Query query = new Query(question);
        long start = System.currentTimeMillis();
        Query rewritten = rewriter.transform(query);
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("original", question);
        result.put("rewritten", rewritten.text());
        result.put("latencyMs", latency);
        return result;
    }

    // ==================== 方式四：MultiQueryExpander ====================

    /**
     * Spring AI 内置 MultiQueryExpander —— 一个问题扩展成多个
     * <p>
     * 把包含多个意图的复杂问题拆成独立的子问题，分别检索后合并结果。
     * 每个子问题单独去检索，召回的文档块会更精准。
     * <p>
     * 参数说明：
     * - numberOfQueries(3)：扩展为3个查询
     * - includeOriginal(true)：结果中包含原始问题
     * <p>
     * 测试：GET /rag/rewrite/expand?question=Redis持久化方式有哪些
     * 预期：扩展为3-4个不同角度的查询
     */
    @GetMapping("/expand")
    public Map<String, Object> expand(@RequestParam("question") String question) {
        MultiQueryExpander expander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                // 扩展为3个查询
                .numberOfQueries(3)
                // 结果中包含原始问题
                .includeOriginal(true)   
                .build();

        Query query = new Query(question);
        long start = System.currentTimeMillis();
        List<Query> expanded = expander.expand(query);
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("original", question);
        result.put("expanded", expanded.stream().map(Query::text).collect(Collectors.toList()));
        result.put("count", expanded.size());
        result.put("latencyMs", latency);
        return result;
    }

    // ==================== 方式五：HyDE 假设性回答 ====================

    /**
     * HyDE（Hypothetical Document Embeddings）—— 假设性回答检索
     * <p>
     * 思路：先让大模型"脑补"一个可能的答案，实际项目中用这个假设答案去检索。
     * 假设答案包含大量领域术语，和知识库文档的用词高度重合，命中率更高。
     * <p>
     * 这里只演示假设回答的生成，不依赖向量库。
     * <p>
     * 测试：GET /rag/rewrite/hyde?question=微服务之间怎么通信
     * 预期：生成一段包含REST、gRPC、RabbitMQ、Kafka等专业术语的假设性回答
     */
    @GetMapping("/hyde")
    public Map<String, Object> hyde(@RequestParam("question") String question) {
        long start = System.currentTimeMillis();
        String hypothetical = chatClient.prompt()
                .user(u -> u.text("""
                    请根据以下问题，生成一段可能的回答。
                    这段回答不需要完全准确，但应该包含相关的专业术语和概念。
                    直接输出回答内容，不要加任何前缀或解释。

                    问题：{question}
                    """).param("question", question))
                .call()
                .content();
        long latency = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("original", question);
        result.put("hypotheticalAnswer", hypothetical);
        result.put("latencyMs", latency);
        result.put("tip", "实际项目中，用这段假设回答的向量去检索，命中率比用原始短问题高");
        return result;
    }

    /**
     * 格式化对话历史，用于接口返回展示
     */
    private List<String> formatHistory(List<Message> history) {
        return history.stream()
                .map(msg -> {
                    String role = msg instanceof UserMessage ? "用户" : "助手";
                    return role + "：" + msg.getText();
                })
                .collect(Collectors.toList());
    }
}
