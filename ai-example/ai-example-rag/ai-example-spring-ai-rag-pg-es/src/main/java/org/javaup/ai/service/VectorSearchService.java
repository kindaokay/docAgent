package org.javaup.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.config.HybridSearchProperties;
import org.javaup.ai.model.SearchResultItem;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 向量语义检索服务。
 * <p>
 * 封装 Spring AI 的 VectorStore（底层是 PGVector），对外提供语义检索能力。
 * 向量检索的工作方式：把查询文本通过 EmbeddingModel 转成向量，
 * 然后在 PGVector 中找余弦距离最近的文档向量，返回语义最相似的文档。
 * <p>
 * 向量检索擅长理解语义（"汽车"能匹配"轿车"），
 * 但不擅长精确匹配关键词（"Redis 7.0" 和 "Redis 6.0" 在向量空间里距离很近）。
 * 所以需要配合关键词检索一起使用，这就是混合检索的意义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    /**
     * Spring AI 的 VectorStore 抽象，这里底层是 PGVector。
     * Spring AI 自动配置会根据 application.yaml 中的 pgvector 配置创建这个 Bean。
     * 写入文档时会自动调用 EmbeddingModel 生成向量，搜索时也会自动把查询文本转成向量。
     */
    private final VectorStore vectorStore;
    private final HybridSearchProperties properties;

    /**
     * 把文档写入 PGVector 向量库。
     * <p>
     * Spring AI 的 VectorStore.add() 内部做了两件事：
     * 1. 调用 EmbeddingModel 把文档文本转成向量（当前配置的是 Qwen3-Embedding-8B，输出 1024 维）
     * 2. 把文本 + 向量 + 元数据一起写入 PGVector 的 vector_store 表
     *
     * @param documents Spring AI Document 列表，每个 Document 包含 id、text、metadata
     */
    public void addDocuments(List<Document> documents) {
        vectorStore.add(documents);
        log.info("已写入 PGVector: {} 个文档", documents.size());
    }

    /**
     * 语义相似度检索（不带过滤条件）。
     * <p>
     * 把查询文本转成向量后，在 PGVector 中找余弦相似度最高的 topK 个文档。
     * similarityThreshold 用于过滤掉相似度太低的噪音结果。
     *
     * @param queryText 用户查询文本
     * @param topK      返回的最大文档数
     * @return 按余弦相似度降序排列的检索结果
     */
    public List<SearchResultItem> searchByVector(String queryText, int topK) {
        // 构建搜索请求
        // Spring AI 会自动把 queryText 通过 EmbeddingModel 转成向量再做检索
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(queryText)
                        .topK(topK)
                        .similarityThreshold(properties.getSimilarityThreshold())
                        .build()
        );

        return convertToResultItems(docs, HybridSearchService.MODE_DENSE_ONLY);
    }

    /**
     * 带分类过滤的语义检索。
     * <p>
     * 在向量检索的基础上加 metadata 过滤条件。
     * Spring AI 的 FilterExpressionBuilder 会把过滤条件翻译成 PGVector 的 SQL WHERE 子句。
     * 先过滤再做向量相似度计算，既缩小了搜索范围，又不影响语义匹配的准确性。
     *
     * @param queryText 用户查询文本
     * @param category  分类过滤条件
     * @param topK      返回的最大文档数
     * @return 过滤后的检索结果
     */
    public List<SearchResultItem> searchByVectorWithCategory(String queryText,
                                                              String category,
                                                              int topK) {
        // 用 FilterExpressionBuilder 构建元数据过滤表达式
        // 生成的表达式类似于 SQL 的 WHERE category = 'database'
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(queryText)
                        .topK(topK)
                        .similarityThreshold(properties.getSimilarityThreshold())
                        .filterExpression(builder.eq("category", category).build())
                        .build()
        );

        return convertToResultItems(docs, HybridSearchService.MODE_DENSE_ONLY);
    }

    /**
     * 直接返回 Spring AI 原始的 Document 列表。
     * 给 HybridSearchService 用的，融合时需要拿到原始的 Document 对象（包含 id）。
     *
     * @param queryText 查询文本
     * @param topK      返回数量
     * @return Spring AI Document 列表
     */
    public List<Document> searchRawDocuments(String queryText, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(queryText)
                        .topK(topK)
                        .similarityThreshold(properties.getSimilarityThreshold())
                        .build()
        );
    }

    /**
     * 返回带分类过滤的原始 Document 列表。
     * <p>
     * 混合检索场景下，如果用户已经明确限定分类，
     * 那向量检索和关键词检索最好都在同一个过滤范围内执行，避免两路结果口径不一致。
     */
    public List<Document> searchRawDocumentsWithCategory(String queryText, String category, int topK) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(queryText)
                        .topK(topK)
                        .similarityThreshold(properties.getSimilarityThreshold())
                        .filterExpression(builder.eq("category", category).build())
                        .build()
        );
    }

    /**
     * 把 Spring AI 的 Document 列表转成统一的 SearchResultItem 列表。
     * <p>
     * Document 的 metadata 里存着我们写入时设置的 title、category 等信息。
     * score 字段是 Spring AI 自动计算的余弦相似度（0~1，越接近 1 越相似）。
     */
    private List<SearchResultItem> convertToResultItems(List<Document> docs, String mode) {
        List<SearchResultItem> results = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();

            // Spring AI 的 Document.getScore() 返回的是相似度分数
            // PGVector + COSINE_DISTANCE 时，分数范围是 0~1
            Double score = doc.getScore();

            results.add(SearchResultItem.builder()
                    .id(doc.getId())
                    .title(metadata != null ? (String) metadata.get("title") : "")
                    .content(doc.getText())
                    .category(metadata != null ? (String) metadata.get("category") : "")
                    .score(score != null ? score : 0.0)
                    .mode(mode)
                    .build());
        }
        return results;
    }

    /**
     * 清空向量库中的所有文档（用于测试时重置数据）
     */
    public void deleteAll() {
        throw new UnsupportedOperationException("请使用 KnowledgeBaseAdminService 通过 SQL TRUNCATE 清空向量表");
    }
}
