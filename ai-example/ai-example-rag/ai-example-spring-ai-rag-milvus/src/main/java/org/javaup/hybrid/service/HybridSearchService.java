package org.javaup.hybrid.service;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.javaup.hybrid.config.HybridMilvusProperties;
import org.javaup.hybrid.model.HybridSearchResult;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * Milvus 原生混合检索服务，支持三种检索模式：
 * <ul>
 *   <li>DENSE_ONLY —— 纯语义向量检索，适合概念性问题（比如"高血压怎么管理"）</li>
 *   <li>SPARSE_ONLY —— 纯 BM25 关键词检索，适合精确匹配场景（比如"二甲双胍 eGFR 禁忌"）</li>
 *   <li>HYBRID —— Dense + Sparse 双路并行，RRF 融合排序，兼顾语义和关键词</li>
 * </ul>
 * <p>
 * 为什么不用 Spring AI 的 VectorStore？
 * 因为 Spring AI 的 MilvusVectorStore 只支持单个 Dense 向量字段，
 * 不支持 Sparse 字段、BM25 Function、HybridSearchReq 这些混合检索必需的能力。
 * 所以检索部分只能用 Milvus SDK V2 的原生 API。
 * 但 Embedding 生成还是走 Spring AI 的 EmbeddingModel，不用手动调 HTTP。
 */
@Slf4j
@Service
public class HybridSearchService {

    /**
     * Milvus SDK V2 客户端，由 MilvusClientV2Config 注册到 Spring 容器。
     * 混合检索的 HybridSearchReq 只有 V2 SDK 才支持。
     */
    private final MilvusClientV2 milvusClient;

    /**
     * Spring AI 的 Embedding 模型，自动注入。
     * 配置在 application.yaml 的 spring.ai.openai.embedding 下，
     * 当前用的是硅基流动的 Qwen3-Embedding-8B，输出 4096 维向量。
     * 查询时调用它把用户输入的文本转成 Dense 向量。
     */
    private final EmbeddingModel embeddingModel;

    /** 混合检索的所有参数配置，对应 application.yaml 中 app.hybrid.milvus 前缀 */
    private final HybridMilvusProperties properties;

    public HybridSearchService(MilvusClientV2 milvusClient,
                               EmbeddingModel embeddingModel,
                               HybridMilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /**
     * 统一检索入口，根据 mode 分发到不同的检索策略。
     * <p>
     * 调用示例：
     * - search("高血压日常怎么管理", "DENSE_ONLY")  → 纯语义检索
     * - search("二甲双胍 eGFR", "SPARSE_ONLY")     → 纯关键词检索
     * - search("糖尿病用药注意事项", "HYBRID")       → 混合检索（推荐）
     *
     * @param queryText 用户的查询文本
     * @param mode      检索模式：DENSE_ONLY / SPARSE_ONLY / HYBRID
     * @return 按相关性排序的检索结果列表
     */
    public List<HybridSearchResult> search(String queryText, String mode) {
        // 根据模式走不同的检索逻辑，最终都返回 Milvus 的 SearchResp
        SearchResp resp = switch (mode.toUpperCase()) {
            case "DENSE_ONLY" -> denseOnlySearch(queryText);
            case "SPARSE_ONLY" -> sparseOnlySearch(queryText);
            default -> hybridSearch(queryText);
        };
        // 把 Milvus 原始响应转成业务友好的结果对象
        return extractResults(resp, mode.toUpperCase());
    }

    /**
     * 纯 Dense 向量检索（只走语义匹配，不走关键词）。
     * <p>
     * 流程：
     * 1. 调用 Spring AI 的 EmbeddingModel，把查询文本转成 4096 维浮点向量
     * 2. 在 Milvus 的 text_dense 字段上做 ANN（近似最近邻）检索
     * 3. 用 COSINE 余弦相似度衡量向量之间的距离
     * <p>
     * 优点：能理解语义，"汽车"能匹配到"轿车"
     * 缺点：对精确关键词不敏感，"eGFR<30"和"eGFR<45"在向量空间里可能很近
     */
    private SearchResp denseOnlySearch(String queryText) {
        // 第一步：把查询文本转成向量
        // embeddingModel.embed() 内部会调用硅基流动的 Embedding API
        float[] queryVector = embeddingModel.embed(queryText);
        // Milvus SDK 要求 List<Float> 类型，所以做一次转换
        List<Float> vectorList = toFloatList(queryVector);

        // 第二步：构建检索参数
        Map<String, Object> searchParams = new HashMap<>();
        // metric_type=COSINE：用余弦相似度衡量向量距离，值越大越相似
        searchParams.put("metric_type", "COSINE");
        // ef：HNSW 检索时的搜索宽度，值越大召回越准但越慢，默认 64
        searchParams.put("ef", properties.getDenseSearchEf());

        // 第三步：发起检索
        return milvusClient.search(SearchReq.builder()
                // 指定在哪个 Collection 上检索
                .collectionName(properties.getCollectionName())
                // 指定在哪个向量字段上做 ANN 检索
                .annsField("text_dense")
                // 传入查询向量，FloatVec 是 Milvus SDK 对浮点向量的封装
                .data(Collections.singletonList(new FloatVec(vectorList)))
                // 最终返回多少条结果
                .topK(properties.getFinalTopK())
                // 除了向量匹配分数，还要返回 text 字段的原始内容
                .outputFields(List.of("text"))
                .searchParams(searchParams)
                // BOUNDED 一致性：允许短暂的数据延迟，换取更好的检索性能
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
    }

    /**
     * 纯 Sparse（BM25）关键词检索（只走关键词匹配，不走语义）。
     * <p>
     * 流程：
     * 1. 把原始查询文本直接传给 Milvus（不需要生成向量！）
     * 2. Milvus 服务端用 BM25 analyzer 对查询文本做分词
     * 3. 在 text_sparse 字段上做倒排索引匹配
     * <p>
     * 优点：精确匹配关键词，"eGFR<30"就是"eGFR<30"，不会和其他数字混淆
     * 缺点：不理解语义，"汽车"匹配不到"轿车"
     */
    private SearchResp sparseOnlySearch(String queryText) {
        Map<String, Object> searchParams = new HashMap<>();
        // metric_type=BM25：使用 BM25 算法做关键词匹配打分
        searchParams.put("metric_type", "BM25");
        // drop_ratio_search：长尾剪枝比例，过滤掉 BM25 得分过低的匹配项
        // 比如 0.15 表示丢弃得分最低的 15% 的匹配结果，减少噪音
        searchParams.put("drop_ratio_search", properties.getDropRatioSearch());

        return milvusClient.search(SearchReq.builder()
                .collectionName(properties.getCollectionName())
                // 在稀疏向量字段上检索
                .annsField("text_sparse")
                // EmbeddedText 是关键！它告诉 Milvus：
                // "这不是一个向量，而是一段原始文本，请你用 BM25 analyzer 帮我分词后再检索"
                // Milvus 收到后会自动对这段文本做分词，然后在倒排索引中查找匹配的文档
                .data(Collections.singletonList(new EmbeddedText(queryText)))
                .topK(properties.getFinalTopK())
                .outputFields(List.of("text"))
                .searchParams(searchParams)
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
    }

    /**
     * Dense + Sparse 混合检索，RRF 融合排序（推荐使用的模式）。
     * <p>
     * 整体流程：
     * 1. Dense 分支：EmbeddingModel 生成查询向量 → 在 text_dense 上做 HNSW ANN 检索 → 召回 12 条
     * 2. Sparse 分支：原始文本 → 在 text_sparse 上做 BM25 关键词匹配 → 召回 12 条
     * 3. RRF 融合：把两路结果按排名融合（不看分数，只看排名），最终返回 5 条
     * <p>
     * 为什么用 RRF 而不是直接把分数加起来？
     * 因为 Dense 的分数是余弦相似度（0~1），BM25 的分数是无界正数（可能是 0.5 也可能是 15.8），
     * 两种分数量纲完全不同，直接相加会让 BM25 的分数碾压 Dense 的分数。
     * RRF 只看排名不看分数，天然解决了这个问题。
     */
    private SearchResp hybridSearch(String queryText) {
        // ===== 第一路：Dense 语义检索 =====
        // 把查询文本转成 4096 维向量
        float[] queryVector = embeddingModel.embed(queryText);
        List<Float> vectorList = toFloatList(queryVector);

        // 构建 Dense 分支的 ANN 检索请求
        AnnSearchReq denseReq = AnnSearchReq.builder()
                // 在 text_dense 字段上检索
                .vectorFieldName("text_dense")
                // 传入查询向量
                .vectors(Collections.singletonList(new FloatVec(vectorList)))
                // HNSW 检索参数：ef 越大，搜索越广，召回越准，但越慢
                .params("{\"ef\": " + properties.getDenseSearchEf() + "}")
                // Dense 分支的召回数量（不是最终返回数量！最终由 RRF 融合后再截断）
                .topK(properties.getDenseRecallTopK())
                .build();

        // ===== 第二路：Sparse（BM25）关键词检索 =====
        AnnSearchReq sparseReq = AnnSearchReq.builder()
                // 在 text_sparse 字段上检索
                .vectorFieldName("text_sparse")
                // 传入原始文本，Milvus 会自动用 BM25 analyzer 分词
                .vectors(Collections.singletonList(new EmbeddedText(queryText)))
                // drop_ratio_search：丢弃 BM25 得分最低的部分匹配，减少噪音
                .params("{\"drop_ratio_search\": " + properties.getDropRatioSearch() + "}")
                // Sparse 分支的召回数量
                .topK(properties.getSparseRecallTopK())
                .build();

        // ===== 第三步：RRF 融合两路结果 =====
        HybridSearchReq hybridReq = HybridSearchReq.builder()
                .collectionName(properties.getCollectionName())
                // 把两个分支的检索请求放在一起，Milvus 会并行执行
                .searchRequests(List.of(denseReq, sparseReq))
                // RRFRanker：倒数排名融合算法
                // 公式：RRF_score(d) = Σ 1/(K + rank_i(d))
                // K=60 是经验值，几乎不需要调。一个文档在两路中排名都靠前，融合分数就高
                .ranker(new RRFRanker(properties.getRrfK()))
                // 融合后最终返回的文档数量
                .topK(properties.getFinalTopK())
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                // 返回 text 字段的原始内容
                .outFields(List.of("text"))
                .build();

        // Milvus 内部会：并行跑两路检索 → 收集结果 → RRF 融合排序 → 截断到 topK
        return milvusClient.hybridSearch(hybridReq);
    }

    /**
     * 从 Milvus 的 SearchResp 中提取结果，转成业务友好的 HybridSearchResult 列表。
     * <p>
     * Milvus 返回的是嵌套结构：外层 List 对应每个查询（我们只有一个查询），
     * 内层 List 是该查询的 TopK 结果，每个结果包含 id、score、entity（字段值）。
     */
    private List<HybridSearchResult> extractResults(SearchResp resp, String mode) {
        List<HybridSearchResult> results = new ArrayList<>();
        // 外层遍历：每个查询的结果（我们只传了一个查询，所以外层只有一个元素）
        List<List<SearchResp.SearchResult>> searchResults = resp.getSearchResults();

        for (List<SearchResp.SearchResult> queryResults : searchResults) {
            // 内层遍历：该查询的 TopK 条匹配结果
            for (SearchResp.SearchResult r : queryResults) {
                // 从 entity 中取出 text 字段的值（就是原始文本内容）
                Object text = r.getEntity() == null ? null : r.getEntity().get("text");
                results.add(new HybridSearchResult(
                        String.valueOf(r.getId()),       // Milvus 主键
                        text == null ? "" : text.toString(), // 原始文本
                        r.getScore(),                    // 匹配分数
                        mode                             // 当前使用的检索模式
                ));
            }
        }

        // 打印检索日志，方便调试和效果对比
        log.info("HybridSearch | mode={} | hits= | topScore={}",
                mode, results.size(),
                results.isEmpty() ? "N/A" : results.get(0).score());
        return results;
    }

    /**
     * float[] 转 List<Float>。
     * <p>
     * Spring AI 的 EmbeddingModel.embed() 返回 float[]，
     * 但 Milvus SDK 的 FloatVec 构造函数要求 List<Float>，所以需要转一下。
     */
    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }
}
