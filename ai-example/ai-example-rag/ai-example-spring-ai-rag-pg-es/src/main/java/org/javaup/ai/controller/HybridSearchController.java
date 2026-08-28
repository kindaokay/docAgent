package org.javaup.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.SearchResultItem;
import org.javaup.ai.model.TechArticle;
import org.javaup.ai.service.HybridSearchService;
import org.javaup.ai.service.KnowledgeBaseAdminService;
import org.javaup.ai.service.KnowledgeImportService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * 混合检索 REST 接口。
 * <p>
 * 提供以下能力：
 * 1. 知识库导入（双写 PGVector + ES）
 * 2. 一个统一的 search 接口，通过 mode 切换 DENSE_ONLY / SPARSE_ONLY / HYBRID
 * 3. 检索模式对比（同一个查询跑三种模式，直观对比效果）
 * 4. 一个统一的 chat 接口，通过 mode 控制 RAG 前的召回方式
 */
@Slf4j
@RestController
@RequestMapping("/hybrid")
@RequiredArgsConstructor
public class HybridSearchController {

    private final HybridSearchService hybridSearchService;
    private final KnowledgeImportService knowledgeImportService;
    private final KnowledgeBaseAdminService knowledgeBaseAdminService;
    private final ChatClient.Builder chatClientBuilder;

    /**
     * RAG 问答的 System Prompt。
     * 告诉大模型：只能根据检索到的参考资料回答，不能编造。
     * {context} 占位符会被替换成混合检索召回的文档内容。
     */
    private static final String RAG_PROMPT = """
            你是一个技术文档助手，擅长回答云原生、数据库、中间件等技术问题。
            请根据以下参考资料回答用户的问题。

            要求：
            1. 只基于参考资料回答，不要编造信息
            2. 如果参考资料中没有相关内容，如实告知用户
            3. 回答要准确、有条理，适当使用技术术语

            参考资料：
            {context}
            """;

    // ==================== 知识库导入 ====================

    /**
     * 导入单篇技术文章到知识库。
     * <p>
     * 文章会同时写入 PGVector（向量检索）和 Elasticsearch（关键词检索）。
     * <p>
     * 请求体示例：
     * {
     *   "title": "Redis 7.0 多线程模型",
     *   "content": "Redis 7.0 引入了多线程 IO...",
     *   "category": "database",
     *   "tags": ["redis", "多线程", "性能优化"]
     * }
     */
    @PostMapping("/import")
    public Map<String, String> importArticle(@RequestBody TechArticle article) {
        String docId = knowledgeImportService.importArticle(article);
        return Map.of("id", docId, "message", "导入成功");
    }

    /**
     * 批量导入技术文章。
     */
    @PostMapping("/import/batch")
    public Map<String, Object> importArticles(@RequestBody List<TechArticle> articles) {
        List<String> ids = knowledgeImportService.importArticles(articles);
        return Map.of("ids", ids, "count", ids.size(), "message", "批量导入成功");
    }

    /**
     * 一键重建演示知识库。
     * <p>
     * 这个接口特别适合文档演示：
     * 1. 先清空 PGVector 和 ES 中的旧数据
     * 2. 再导入一批已经准备好的技术文章
     * 3. 导入完成后立刻就能调用检索接口做效果对比
     */
    @PostMapping("/init")
    public Map<String, Object> initDemoKnowledgeBase() {
        return knowledgeBaseAdminService.rebuildDemoKnowledgeBase();
    }

    /**
     * 查看当前知识库状态，确认双系统里分别有多少条数据。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return knowledgeBaseAdminService.getStatus();
    }

    // ==================== 检索接口 ====================

    /**
     * 统一检索接口，风格和 Milvus 混合检索示例保持一致。
     * <p>
     * 示例请求：
     * - GET /hybrid/search?query=Redis 7.0多线程模型&mode=HYBRID
     * - GET /hybrid/search?query=K8s 1.28 Sidecar&mode=SPARSE_ONLY
     * - GET /hybrid/search?query=容器启动失败怎么排查&mode=DENSE_ONLY
     * <p>
     * mode 可选值：
     * - DENSE_ONLY：只走 PGVector 语义检索
     * - SPARSE_ONLY：只走 Elasticsearch BM25 检索
     * - HYBRID：双路召回 + RRF 融合（默认）
     * <p>
     * 同时兼容旧值 VECTOR_ONLY / KEYWORD_ONLY，避免已有调试脚本一下子失效。
     */
    @GetMapping("/search")
    public List<SearchResultItem> search(@RequestParam("query") String query,
                                         @RequestParam(name = "mode", defaultValue = "HYBRID") String mode,
                                         @RequestParam(name = "category", required = false) String category) {
        return hybridSearchService.search(query, mode, category);
    }

    /**
     * 三种模式对比检索。
     * <p>
     * 同一个查询同时跑向量、关键词、混合三种模式，返回各自的结果。
     * 方便直观对比不同检索方式的效果差异。
     * <p>
     * 推荐用这些查询来测试效果差异：
     * - "Redis 7.0 多线程"（含版本号，关键词检索优势明显）
     * - "容器出了问题怎么排查"（语义模糊，向量检索优势明显）
     * - "K8s 1.28 Sidecar 容器"（版本号 + 专有名词，混合检索效果最好）
     * <p>
     * 示例：curl "http://localhost:7095/hybrid/compare?query=K8s 1.28 Sidecar容器"
     */
    @GetMapping("/compare")
    public Map<String, List<SearchResultItem>> compareSearch(@RequestParam("query") String query,
                                                             @RequestParam(name = "category", required = false) String category) {
        return hybridSearchService.compareSearch(query, category);
    }

    // ==================== RAG 问答 ====================

    /**
     * 统一的 RAG 问答接口（流式返回）。
     * <p>
     * 完整流程：
     * 1. 按 mode 选择检索方式：DENSE_ONLY / SPARSE_ONLY / HYBRID
     * 2. 拼上下文：把召回的文档内容拼成一段参考资料
     * 3. 大模型生成：把参考资料和用户问题一起发给大模型，流式返回答案
     * <p>
     * 示例：
     * - GET /hybrid/chat?question=Redis的多线程模型是怎么工作的&mode=HYBRID
     * - GET /hybrid/chat?question=K8s 1.28 Sidecar 是什么&mode=SPARSE_ONLY
     */
    @GetMapping("/chat")
    public Flux<String> hybridChat(@RequestParam("question") String question,
                                   @RequestParam(name = "mode", defaultValue = "HYBRID") String mode,
                                   @RequestParam(name = "category", required = false) String category) {
        // 第一步：按 mode 选择检索方式召回相关文档
        List<SearchResultItem> docs = hybridSearchService.search(question, mode, category);

        if (docs.isEmpty()) {
            return Flux.just("抱歉，没有找到相关的参考资料，无法回答这个问题。");
        }

        // 第二步：把召回的文档内容拼成上下文
        // 每篇文档之间用分隔线隔开，方便大模型区分不同的参考来源
        String context = docs.stream()
                .map(item -> {
                    StringBuilder sb = new StringBuilder();
                    if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                        sb.append("【").append(item.getTitle()).append("】\n");
                    }
                    sb.append(item.getContent());
                    return sb.toString();
                })
                .collect(Collectors.joining("\n---\n"));

        log.info("混合检索 RAG: question={}, 召回文档数={}", question, docs.size());

        // 第三步：调用大模型生成回答（流式输出）
        ChatClient chatClient = chatClientBuilder.build();
        return chatClient.prompt()
                .system(s -> s.text(RAG_PROMPT).param("context", context))
                .user(question)
                .stream()
                .content();
    }
}
