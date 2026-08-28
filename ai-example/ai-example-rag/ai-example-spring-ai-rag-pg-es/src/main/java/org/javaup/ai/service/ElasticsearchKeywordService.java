package org.javaup.ai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.config.HybridSearchProperties;
import org.javaup.ai.model.EsArticleDocument;
import org.javaup.ai.model.SearchResultItem;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * Elasticsearch 关键词检索服务。
 * <p>
 * 负责两件事：
 * 1. 把文档写入 ES 索引（建立倒排索引，为 BM25 检索做准备）
 * 2. 用 BM25 算法做关键词检索（精确匹配版本号、专有名词等向量检索搞不定的场景）
 * <p>
 * 这个服务只管关键词检索这一路，不涉及向量检索。
 * 混合检索的编排和 RRF 融合由 HybridSearchService 负责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchKeywordService {

    private final ElasticsearchClient esClient;
    private final HybridSearchProperties properties;

    /**
     * 把单个文档写入 ES 索引。
     * <p>
     * 用 chunk_id 作为 ES 文档的 _id，这样同一个文档在 PGVector 和 ES 里用的是同一个 ID。
     * RRF 融合时就靠这个 ID 来判断两路检索返回的是不是同一篇文档。
     *
     * @param chunkId  文档唯一标识，和 PGVector 中 Document 的 id 一致
     * @param title    文章标题
     * @param content  文章正文
     * @param category 文章分类
     * @param tags     标签列表
     */
    public void indexDocument(String chunkId, String title, String content,
                              String category, List<String> tags) {
        indexDocument(EsArticleDocument.builder()
                .chunkId(chunkId)
                .title(title)
                .content(content)
                .category(category)
                .tags(tags != null ? tags : List.of())
                .build());
    }

    /**
     * 写入一篇结构化的 ES 文档。
     */
    public void indexDocument(EsArticleDocument document) {
        try {
            esClient.index(i -> i
                    .index(properties.getEsIndexName())
                    // 用 chunk_id 作为 ES 的文档 _id，保证和 PGVector 的 ID 对齐
                    .id(document.getChunkId())
                    // WaitFor 可以让这次写入在 refresh 后再返回。
                    // 这样演示接口里“刚导入就马上搜索”时，结果不会因为 ES 刷新延迟而查不到。
                    .refresh(Refresh.WaitFor)
                    .document(document)
            );
            log.debug("文档已写入 ES: chunkId={}, title={}", document.getChunkId(), document.getTitle());
        } catch (IOException e) {
            log.error("写入 ES 失败: chunkId={}, 原因: {}", document.getChunkId(), e.getMessage(), e);
            throw new RuntimeException("写入 ES 失败", e);
        }
    }

    /**
     * 批量写入多个文档到 ES 索引。
     * <p>
     * 用 Bulk API 一次性提交，比逐条写入效率高很多。
     * 适合初始化数据或批量导入场景。
     *
     * @param documents 文档列表
     */
    public void bulkIndex(List<EsArticleDocument> documents) {
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder()
                    .index(properties.getEsIndexName())
                    .refresh(Refresh.WaitFor);

            for (EsArticleDocument document : documents) {
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .id(document.getChunkId())
                                .document(document)
                        )
                );
            }

            BulkResponse response = esClient.bulk(bulkBuilder.build());

            // 检查是否有失败的文档
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("批量写入 ES 部分失败: docId={}, 原因={}",
                                item.id(), item.error().reason());
                    }
                }
            }
            log.info("批量写入 ES 完成: 总数={}, 耗时={}ms", documents.size(), response.took());

        } catch (IOException e) {
            log.error("批量写入 ES 失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量写入 ES 失败", e);
        }
    }

    /**
     * BM25 关键词检索。
     * <p>
     * 同时搜索 title 和 content 两个字段，用 multi_match 查询。
     * BM25 是 ES 默认的评分算法，擅长精确匹配关键词、版本号、专有名词。
     * <p>
     * 比如用户搜 "Redis 7.0 多线程"，BM25 能精准匹配到包含 "Redis 7.0" 的文档，
     * 不会像向量检索那样把 "Redis 6.0" 和 "Redis 7.0" 混为一谈。
     *
     * @param queryText 用户查询文本
     * @param topK      返回的最大文档数
     * @return 按 BM25 分数降序排列的检索结果
     */
    public List<SearchResultItem> searchByKeyword(String queryText, int topK) {
        try {
            SearchResponse<EsArticleDocument> response = esClient.search(s -> s
                            .index(properties.getEsIndexName())
                            .query(q -> q
                                    // multi_match：同时在 title 和 content 两个字段上搜索
                                    // title 加了 ^2 的权重提升，标题匹配的结果排名更靠前
                                    .multiMatch(mm -> mm
                                            .query(queryText)
                                            .fields("title^2", "content")
                                            // best_fields：取各字段中得分最高的那个作为最终得分
                                            // 适合关键词集中在某一个字段的场景
                                            .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                                    )
                            )
                            .size(topK),
                    EsArticleDocument.class
            );

            // 把 ES 返回的 Hit 转成统一的 SearchResultItem
            List<SearchResultItem> results = new ArrayList<>();
            for (Hit<EsArticleDocument> hit : response.hits().hits()) {
                EsArticleDocument source = hit.source();
                if (source == null) {
                    continue;
                }
                results.add(SearchResultItem.builder()
                        .id(source.getChunkId())
                        .title(source.getTitle())
                        .content(source.getContent())
                        .category(source.getCategory())
                        .score(hit.score() != null ? hit.score() : 0.0)
                        .mode(HybridSearchService.MODE_SPARSE_ONLY)
                        .build());
            }

            log.debug("ES 关键词检索完成: query={}, 命中={}", queryText, results.size());
            return results;

        } catch (IOException e) {
            log.error("ES 关键词检索失败: query={}, 原因: {}", queryText, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 带分类过滤的关键词检索。
     * <p>
     * 在 BM25 检索的基础上加一个 category 过滤条件。
     * 比如只在 "database" 分类下搜索，过滤掉其他分类的噪音。
     *
     * @param queryText 用户查询文本
     * @param category  分类过滤条件
     * @param topK      返回的最大文档数
     * @return 过滤后的检索结果
     */
    public List<SearchResultItem> searchByKeywordWithCategory(String queryText,
                                                              String category,
                                                              int topK) {
        try {
            SearchResponse<EsArticleDocument> response = esClient.search(s -> s
                            .index(properties.getEsIndexName())
                            .query(q -> q
                                    // bool 查询：must 做全文检索，filter 做分类过滤
                                    .bool(b -> b
                                            .must(m -> m.multiMatch(mm -> mm
                                                    .query(queryText)
                                                    .fields("title^2", "content")
                                            ))
                                            // filter 不参与评分，纯过滤，性能更好
                                            .filter(f -> f.term(t -> t
                                                    .field("category")
                                                    .value(category)
                                            ))
                                    )
                            )
                            .size(topK),
                    EsArticleDocument.class
            );

            List<SearchResultItem> results = new ArrayList<>();
            for (Hit<EsArticleDocument> hit : response.hits().hits()) {
                EsArticleDocument source = hit.source();
                if (source == null) {
                    continue;
                }
                results.add(SearchResultItem.builder()
                        .id(source.getChunkId())
                        .title(source.getTitle())
                        .content(source.getContent())
                        .category(source.getCategory())
                        .score(hit.score() != null ? hit.score() : 0.0)
                        .mode(HybridSearchService.MODE_SPARSE_ONLY)
                        .build());
            }
            return results;

        } catch (IOException e) {
            log.error("ES 分类检索失败: query={}, category={}", queryText, category, e);
            return List.of();
        }
    }

    /**
     * 删除索引中的所有文档（用于测试时重置数据）
     */
    public void deleteAllDocuments() {
        try {
            esClient.deleteByQuery(d -> d
                    .index(properties.getEsIndexName())
                    .refresh(true)
                    .query(q -> q.matchAll(ma -> ma))
            );
            log.info("ES 索引 [{}] 所有文档已删除", properties.getEsIndexName());
        } catch (IOException e) {
            log.error("删除 ES 文档失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 返回 ES 索引里的文档数量，方便做状态检查和初始化结果确认。
     */
    public long countDocuments() {
        try {
            return esClient.count(c -> c.index(properties.getEsIndexName())).count();
        } catch (IOException e) {
            throw new RuntimeException("统计 ES 文档数量失败", e);
        }
    }
}
