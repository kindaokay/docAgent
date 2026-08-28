package org.javaup.ai.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 配置类
 * @author: 阿星不是程序员
 **/
/**
 * Elasticsearch 索引初始化配置。
 * <p>
 * 应用启动时自动检查目标索引是否存在，不存在则创建。
 * 索引的 mapping 定义了每个字段的类型和分词器：
 * - title 和 content 用 text 类型 + IK 分词器，支持中文全文检索
 * - category 和 tags 用 keyword 类型，支持精确过滤
 * - chunk_id 用 keyword 类型，用于和 PGVector 的文档 ID 做关联
 * <p>
 * 注意：IK 分词器需要在 Elasticsearch 上单独安装插件。
 * 安装命令：./bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/8.x.x
 * 如果未安装 IK 插件，请在 application.yaml 中把 es-analyzer 和 es-search-analyzer 改为 standard
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient esClient;
    private final HybridSearchProperties properties;

    /**
     * 应用启动后自动执行，确保 ES 索引就绪
     */
    @PostConstruct
    public void initIndex() {
        String indexName = properties.getEsIndexName();
        String analyzer = properties.getEsAnalyzer();
        String searchAnalyzer = properties.getEsSearchAnalyzer();

        try {
            // 先检查索引是否已经存在，避免重复创建报错
            boolean exists = indexExists(indexName);

            if (exists) {
                log.info("ES 索引 [{}] 已存在，跳过创建", indexName);
                return;
            }

            createIndex(indexName, analyzer, searchAnalyzer);
            log.info("ES 索引 [{}] 创建成功，分词器: {}，搜索分词器: {}",
                    indexName, analyzer, searchAnalyzer);
        } catch (IOException e) {
            // 很多人本地第一次跑示例时并没有安装 IK 插件，
            // 所以这里做一个轻量兜底：如果 IK 创建失败，就自动回退到 standard 分词器。
            if (isIkAnalyzer(properties.getEsAnalyzer()) || isIkAnalyzer(properties.getEsSearchAnalyzer())) {
                log.warn("使用 IK 分词器创建 ES 索引失败，准备回退到 standard。原因: {}", e.getMessage());
                fallbackToStandard(indexName);
                return;
            }

            // 索引创建失败不影响应用启动，只打日志
            // 实际生产环境可能需要更严格的处理
            log.error("ES 索引初始化失败: {}", e.getMessage(), e);
        }
    }

    private boolean indexExists(String indexName) throws IOException {
        return esClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexName)))
                .value();
    }

    private void createIndex(String indexName, String analyzer, String searchAnalyzer) throws IOException {
        esClient.indices().create(c -> c
                .index(indexName)
                .mappings(m -> m
                        // chunk_id：和 PGVector 中 Document 的 id 对应，用于 RRF 融合时跨系统去重
                        .properties("chunk_id", p -> p.keyword(k -> k))
                        // title：文章标题，用 text 类型支持全文检索
                        .properties("title", p -> p.text(t -> t
                                .analyzer(analyzer)
                                .searchAnalyzer(searchAnalyzer)))
                        // content：文章正文，配置同 title
                        .properties("content", p -> p.text(t -> t
                                .analyzer(analyzer)
                                .searchAnalyzer(searchAnalyzer)))
                        // category：分类字段，keyword 类型用于精确匹配和过滤聚合
                        .properties("category", p -> p.keyword(k -> k))
                        // tags：标签数组，keyword 类型用于精确过滤
                        .properties("tags", p -> p.keyword(k -> k))
                )
        );
    }

    private boolean isIkAnalyzer(String analyzer) {
        return analyzer != null && analyzer.startsWith("ik_");
    }

    private void fallbackToStandard(String indexName) {
        try {
            if (indexExists(indexName)) {
                log.info("ES 索引 [{}] 已存在，跳过 standard 回退创建", indexName);
                return;
            }

            createIndex(indexName, "standard", "standard");
            log.info("ES 索引 [{}] 已回退到 standard 分词器创建成功", indexName);
        } catch (IOException ex) {
            log.error("ES 索引 standard 回退创建仍然失败: {}", ex.getMessage(), ex);
        }
    }
}
