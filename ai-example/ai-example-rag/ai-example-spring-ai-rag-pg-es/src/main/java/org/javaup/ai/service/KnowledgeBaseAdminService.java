package org.javaup.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.config.HybridSearchProperties;
import org.javaup.ai.model.TechArticle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 知识库管理服务。
 * <p>
 * 这个服务不负责检索，它只做三件事：
 * 1. 清空双系统里的旧数据
 * 2. 导入一批演示文章，让示例项目开箱即用
 * 3. 提供状态信息，方便确认 ES 和 PGVector 是否都写进去了
 * <p>
 * 之所以单独拆出来，是为了避免把“搜索逻辑”和“演示环境管理”混在一个类里。
 * 但这里仍然保持很轻量，不做复杂的仓储层和事务编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeImportService knowledgeImportService;
    private final ElasticsearchKeywordService elasticsearchKeywordService;
    private final HybridSearchProperties properties;

    /**
     * 重建整套演示知识库：
     * 1. 清空向量表
     * 2. 清空 ES 索引
     * 3. 导入新的演示数据
     *
     * @return 导入后的统计信息，便于接口直接返回
     */
    public Map<String, Object> rebuildDemoKnowledgeBase() {
        resetKnowledgeBase();

        List<TechArticle> demoArticles = buildDemoArticles();
        List<String> ids = knowledgeImportService.importArticles(demoArticles);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("message", "PGVector + Elasticsearch 演示知识库初始化完成");
        result.put("insertedCount", ids.size());
        result.put("vectorDocumentCount", countVectorDocuments());
        result.put("keywordDocumentCount", elasticsearchKeywordService.countDocuments());
        result.put("esIndexName", properties.getEsIndexName());
        result.put("vectorTableName", properties.getVectorTableName());
        return result;
    }

    /**
     * 清空双系统中的已有数据。
     * <p>
     * 向量库这边直接对底层表执行 TRUNCATE。
     * 原因很简单：Spring AI VectorStore 没有提供“删除全部文档”的直观 API，
     * 用 SQL 反而最清楚，也更适合示例项目。
     */
    public void resetKnowledgeBase() {
        String truncateSql = "TRUNCATE TABLE public." + properties.getVectorTableName();
        jdbcTemplate.execute(truncateSql);
        log.info("已清空 PGVector 底层表: {}", properties.getVectorTableName());

        elasticsearchKeywordService.deleteAllDocuments();
        log.info("已清空 ES 索引: {}", properties.getEsIndexName());
    }

    /**
     * 返回当前知识库状态。
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("esIndexName", properties.getEsIndexName());
        status.put("vectorTableName", properties.getVectorTableName());
        status.put("initializeOnStartup", properties.isInitializeOnStartup());
        status.put("resetOnStartup", properties.isResetOnStartup());
        status.put("vectorDocumentCount", countVectorDocuments());
        status.put("keywordDocumentCount", elasticsearchKeywordService.countDocuments());
        return status;
    }

    /**
     * 统计 PGVector 底层表中的文档条数。
     */
    public long countVectorDocuments() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public." + properties.getVectorTableName(),
                Long.class
        );
        return count != null ? count : 0L;
    }

    /**
     * 构造一批适合演示混合检索价值的技术文章。
     * <p>
     * 这里故意混合了：
     * - 版本号查询
     * - 专有名词查询
     * - 偏语义表达的查询
     * 这样调用 /hybrid/compare 时更容易看出向量检索、BM25、混合检索的差异。
     */
    private List<TechArticle> buildDemoArticles() {
        return List.of(
                TechArticle.builder()
                        .id("redis-7-io-model")
                        .title("Redis 7.0 多线程 I/O 模型拆解")
                        .category("database")
                        .tags(List.of("redis", "7.0", "多线程", "io"))
                        .content("""
                                Redis 7.0 仍然把命令执行放在主线程里，但网络读写阶段继续复用多线程 I/O。
                                这样做的重点不是把所有逻辑并行化，而是把最容易成为瓶颈的 socket 读写、协议解析前置处理拆出去。
                                如果你在排查 Redis 7.0 的吞吐提升，应该先关注网络 I/O、连接数和 pipeline，而不是误以为命令执行已经完全多线程化。
                                对于“Redis 7.0 多线程模型”和“Redis 7.0 I/O 线程”这类带版本号的问题，关键词检索通常会比纯向量检索更稳。
                                """)
                        .build(),
                TechArticle.builder()
                        .id("redis-6-thread-model")
                        .title("Redis 6.0 线程模型演进与性能关注点")
                        .category("database")
                        .tags(List.of("redis", "6.0", "线程模型", "性能"))
                        .content("""
                                Redis 6.0 开始引入多线程 I/O，目标主要是缓解高并发连接下的网络读写压力。
                                但核心命令执行、数据结构操作和事务语义依然保持在主线程中完成。
                                所以如果有人问“Redis 从 6.0 开始是不是完全多线程了”，答案是否定的。
                                这篇文章适合和 Redis 7.0 版本一起对比，看不同版本在线程模型上的取舍。
                                """)
                        .build(),
                TechArticle.builder()
                        .id("k8s-1-28-sidecar")
                        .title("Kubernetes 1.28 Sidecar 容器机制和探针配置")
                        .category("container")
                        .tags(List.of("kubernetes", "1.28", "sidecar", "probe"))
                        .content("""
                                Kubernetes 1.28 对 Sidecar 容器场景的支持更清晰，典型用法是把日志采集、代理转发这类辅助能力和主业务容器绑定在一起。
                                在健康检查设计上，startupProbe 适合给慢启动应用争取初始化时间，readinessProbe 决定流量是否进入，livenessProbe 则负责发现僵死进程。
                                如果用户的问题不是直说“探针”，而是说“容器一直启动不起来怎么排查”，向量检索更容易把这篇内容召回出来。
                                但当用户明确输入“K8s 1.28 Sidecar”时，关键词检索对版本号和专有词的命中会更稳。
                                """)
                        .build(),
                TechArticle.builder()
                        .id("spring-boot-3-2-startup")
                        .title("Spring Boot 3.2 启动阶段里的观测点和自动配置排查")
                        .category("framework")
                        .tags(List.of("spring-boot", "3.2", "启动流程", "自动配置"))
                        .content("""
                                Spring Boot 3.2 的启动过程依然围绕环境准备、容器创建、自动配置装配和应用就绪几个阶段展开。
                                如果你要排查启动慢，建议先看 AutoConfigurationImportSelector 相关日志、条件装配结果以及应用事件时间线。
                                对于“启动流程有什么变化”这种表达，向量检索容易抓到主题；
                                但如果问题里带了“3.2”这种精确版本号，混合检索会更保险。
                                """)
                        .build(),
                TechArticle.builder()
                        .id("nacos-2-3-grpc")
                        .title("Nacos 2.3 客户端 gRPC 长连接机制说明")
                        .category("middleware")
                        .tags(List.of("nacos", "2.3", "grpc", "长连接"))
                        .content("""
                                Nacos 2.x 一个明显变化是客户端和服务端之间大量通信改成了 gRPC 长连接，不再完全依赖旧的 HTTP 轮询方式。
                                这意味着在排查注册失败、配置推送延迟时，除了看 namespace 和 group，也要看 gRPC 端口、连接复用以及心跳状态。
                                “Nacos 2.3 gRPC 长连接”这种问题里，gRPC 和 2.3 都是非常强的关键词，纯 BM25 往往能一下命中。
                                """)
                        .build(),
                TechArticle.builder()
                        .id("es-ik-analyzer")
                        .title("Elasticsearch IK 分词器在中文技术文档里的用法")
                        .category("search")
                        .tags(List.of("elasticsearch", "ik", "bm25", "中文分词"))
                        .content("""
                                Elasticsearch 默认 standard analyzer 对中文支持比较有限，所以做中文技术知识库时，通常会装 IK 分词器。
                                常见组合是索引阶段用 ik_max_word 提高召回，搜索阶段用 ik_smart 控制噪音。
                                当查询里出现“连接池泄漏”“线程上下文传播”这种中文技术词组时，IK 分词通常比默认分词效果更稳定。
                                这也是 ES + 向量库双系统方案在中文场景里依然很常见的原因。
                                """)
                        .build(),
                TechArticle.builder()
                        .id("pgvector-hnsw")
                        .title("PGVector 使用 HNSW 和余弦距离做语义检索")
                        .category("database")
                        .tags(List.of("pgvector", "hnsw", "cosine", "embedding"))
                        .content("""
                                在 Spring AI 里接 PGVector 时，常见配置是 HNSW 索引加余弦距离，这样更适合 embedding 语义检索。
                                文档写入时，VectorStore 会先调用 embedding 模型生成向量，再把原文、metadata 和向量一起落到 PostgreSQL。
                                如果查询像“怎么根据语义找相似知识片段”，哪怕用户没有明确说出 PGVector，也有机会通过向量检索把这篇文档召回。
                                """)
                        .build()
        );
    }
}
