package org.javaup.rerank;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 后置处理器
 * @author: 阿星不是程序员
 **/
/**
 * 基于SiliconFlow Rerank API实现Spring AI的DocumentPostProcessor
 * <p>
 * 为什么要实现DocumentPostProcessor而不是自己写一个独立的Service？
 * 因为DocumentPostProcessor是Spring AI RAG模块的标准接口，
 * 实现它之后可以直接嵌入RetrievalAugmentationAdvisor的流水线，
 * 也可以单独注入后手动调用，两种用法都兼容。
 * <p>
 * SiliconFlow的 /v1/rerank 接口兼容多种Reranker模型，
 * 如 BAAI/bge-reranker-v2-m3、Qwen/Qwen3-Reranker-8B 等，
 * 通过配置 rerank.model 即可切换，不用改代码。
 * <p>
 * 使用方式：
 * 1. 作为独立Bean注入后手动调用 process(query, docs)
 * 2. 配合 RetrievalAugmentationAdvisor 的 documentPostProcessors 自动嵌入RAG链路
 */
@Slf4j
@Component
public class SiliconFlowRerankPostProcessor implements DocumentPostProcessor {

    /**
     * SiliconFlow的Rerank API地址
     * 这个接口兼容OpenAI的rerank协议，请求体和返回体格式是通用的
     */
    private static final String RERANK_URL = "https://api.siliconflow.cn/v1/rerank";

    private final RestTemplate restTemplate = new RestTemplate();

    /** SiliconFlow的API Key，复用spring.ai.openai.api-key配置，不用额外配 */
    private final String apiKey;

    /** Reranker模型名称，默认bge-reranker-v2-m3，也可以换成Qwen/Qwen3-Reranker-8B */
    private final String model;

    /** 重排序后返回前N个文档，通常取3-5个送入大模型 */
    private final int topN;

    public SiliconFlowRerankPostProcessor(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${rerank.model:BAAI/bge-reranker-v2-m3}") String model,
            @Value("${rerank.top-n:3}") int topN) {
        this.apiKey = apiKey;
        this.model = model;
        this.topN = topN;
    }

    /**
     * 核心方法：对候选文档做重排序
     * <p>
     * 流程：
     * 1. 从Spring AI的Document对象中提取纯文本，组装成API需要的字符串列表
     * 2. 调用SiliconFlow的 /v1/rerank 接口，把查询和所有候选文档一起发过去
     * 3. API返回每个文档的index和relevance_score
     * 4. 按score降序排列，取前topN个，把score写入Document的metadata
     * 5. 如果API调用失败，降级返回原始顺序的前topN个（不影响主流程）
     *
     * @param query     Spring AI的Query对象，包含用户的查询文本
     * @param documents 候选文档列表，通常是向量检索或混合检索的结果
     * @return 重排序后的文档列表（最多topN个），metadata中包含rerank_score
     */
    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public List<Document> process(@NotNull Query query, @NotNull List<Document> documents) {
        if (CollectionUtil.isEmpty(documents)) {
            return documents;
        }

        // 第一步：把Document对象转成纯文本列表，这是Rerank API需要的输入格式
        List<String> texts = documents.stream()
                .map(Document::getText)
                .toList();

        // 第二步：构造HTTP请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        /*
         * 请求体说明：
         * - model：使用哪个Reranker模型
         * - query：用户的查询文本
         * - documents：候选文档的文本列表
         * - top_n：只返回得分最高的N个结果（减少返回数据量）
         * - return_documents：false表示不要在响应中返回文档原文
         *   （因为文档内容我们本地已经有了，只需要拿到index和score就够了，省带宽）
         */
        Map<String, Object> body = Map.of(
                "model", model,
                "query", query.text(),
                "documents", texts,
                "top_n", Math.min(topN, documents.size()),
                "return_documents", false
        );

        try {
            // 第三步：调用Rerank API
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RERANK_URL, new HttpEntity<>(body, headers), Map.class);

            /*
             * 响应体结构：
             * {
             *   "results": [
             *     {"index": 3, "relevance_score": 0.9876},
             *     {"index": 1, "relevance_score": 0.8234},
             *     ...
             *   ]
             * }
             * index 是文档在原始列表中的位置，relevance_score 是相关性得分（0~1）
             */
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) response.getBody().get("results");

            // 第四步：按score降序排列，通过index找回原始Document对象，把score写入metadata
            return results.stream()
                    .sorted(Comparator.comparingDouble(
                            r -> -((Number) r.get("relevance_score")).doubleValue()))
                    .map(r -> {
                        int index = ((Number) r.get("index")).intValue();
                        double score = ((Number) r.get("relevance_score")).doubleValue();
                        // 通过index从原始列表中找到对应的Document
                        Document doc = documents.get(index);
                        // 把rerank分数写入metadata，后续可以用来排查或展示
                        doc.getMetadata().put("rerank_score", score);
                        log.info("Rerank | score={} | text={}...",
                                String.format("%.4f", score),
                                doc.getText().substring(0, Math.min(50, doc.getText().length())));
                        return doc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 降级策略：Reranker挂了不能影响主流程，直接按原始顺序截取前topN个返回
            log.error("Reranker调用失败，返回原始顺序", e);
            return documents.stream().limit(topN).collect(Collectors.toList());
        }
    }
}
