package org.javaup.ai.service;

import org.javaup.ai.config.MilvusDemoProperties;
import org.javaup.ai.model.MilvusSearchResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.milvus.MilvusSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 对 Spring AI 的 Milvus 检索请求做一层包装，把 demo 里常用的过滤与参数收敛到一起。
 */
@Service
public class MilvusSearchService {

    private final VectorStore vectorStore;
    private final MilvusDemoProperties demoProperties;

    public MilvusSearchService(VectorStore vectorStore, MilvusDemoProperties demoProperties) {
        this.vectorStore = vectorStore;
        this.demoProperties = demoProperties;
    }

    public List<MilvusSearchResult> search(String query,
                                           Integer topK,
                                           String category,
                                           String docId,
                                           Double similarityThreshold,
                                           Integer ef) {
        // topK 决定最终返回多少条最相似结果。
        // 它控制的是“本次检索最多拿回多少个候选 chunk 参与后续链路”。
        // searchParamsJson 会把附加检索参数传给 Milvus，这里主要控制 HNSW 的 ef。
        // 经验上 ef 可以设置为 topK 的 4~16 倍，demo 默认给了 64，兼顾速度和召回率。
        MilvusSearchRequest.MilvusBuilder builder = MilvusSearchRequest.milvusBuilder()
                .query(query)
                .topK(normalizeTopK(topK))
                .similarityThreshold(normalizeSimilarityThreshold(similarityThreshold))
                .searchParamsJson("{\"ef\":" + normalizeEf(ef) + "}");

        Filter.Expression filterExpression = buildFilterExpression(category, docId);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(builder.build())
                .stream()
                .map(this::toSearchResult)
                .toList();
    }

    private Filter.Expression buildFilterExpression(String category, String docId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = null;

        // Spring AI 的 filterExpression 最终会转换成 Milvus 可执行的 filter 表达式，
        // metadata 中的 key 会映射成 metadata["category"]、metadata["docId"] 这种形式。
        if (StringUtils.hasText(category)) {
            filter = builder.eq("category", category);
        }
        if (StringUtils.hasText(docId)) {
            FilterExpressionBuilder.Op docFilter = builder.eq("docId", docId);
            filter = (filter == null) ? docFilter : builder.and(filter, docFilter);
        }
        return filter == null ? null : filter.build();
    }

    private MilvusSearchResult toSearchResult(Document document) {
        // Spring AI 会把文档主键、文本内容、相似度分数和 metadata 一起返回，
        // 我们再从 metadata 中拆出 docId/category，整理成更适合接口返回的结构。
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        return new MilvusSearchResult(
                document.getId(),
                document.getText(),
                document.getScore(),
                valueOf(metadata.get("docId")),
                valueOf(metadata.get("category")),
                metadata
        );
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return demoProperties.getTopK();
        }
        return topK;
    }

    private double normalizeSimilarityThreshold(Double similarityThreshold) {
        if (similarityThreshold == null) {
            return demoProperties.getSimilarityThreshold();
        }
        return similarityThreshold;
    }

    private int normalizeEf(Integer ef) {
        if (ef == null || ef <= 0) {
            return demoProperties.getDefaultEf();
        }
        return ef;
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
