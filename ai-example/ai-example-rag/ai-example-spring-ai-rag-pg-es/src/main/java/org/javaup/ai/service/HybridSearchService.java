package org.javaup.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.config.HybridSearchProperties;
import org.javaup.ai.model.SearchResultItem;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 混合检索服务 —— 整个模块的核心。
 * <p>
 * 编排向量检索和关键词检索两条路径，然后用 RRF 算法把两路结果融合成一个排序列表。
 * <p>
 * 工作流程：
 * 1. 向量路径：查询文本 → EmbeddingModel 生成向量 → PGVector 做 ANN 检索 → 召回 N 篇文档
 * 2. 关键词路径：查询文本 → ES 做 BM25 全文检索 → 召回 N 篇文档
 * 3. 融合：两路结果按排名做 RRF 融合 → 去重排序 → 返回 Top-K
 * <p>
 * 同时支持三种检索模式，方便对比效果：
 * - DENSE_ONLY：只走向量检索
 * - SPARSE_ONLY：只走关键词检索
 * - HYBRID：向量 + 关键词混合检索（推荐）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    public static final String MODE_HYBRID = "HYBRID";
    public static final String MODE_DENSE_ONLY = "DENSE_ONLY";
    public static final String MODE_SPARSE_ONLY = "SPARSE_ONLY";

    private final VectorSearchService vectorSearchService;
    private final ElasticsearchKeywordService esKeywordService;
    private final HybridSearchProperties properties;

    /**
     * 混合检索：向量 + 关键词双路并行，RRF 融合排序。
     * <p>
     * 这是推荐使用的检索模式。两条路径互补：
     * - 向量检索负责捕捉语义相关的文档（"容器编排" 能匹配 "Pod 调度"）
     * - 关键词检索负责捕捉精确匹配的文档（"K8s 1.28" 只匹配带这个版本号的文档）
     * - RRF 融合把两路结果合并，在两路中都排名靠前的文档得分最高
     *
     * @param queryText 用户查询文本
     * @return 融合后的检索结果，按 RRF 分数降序排列
     */
    public List<SearchResultItem> hybridSearch(String queryText) {
        return hybridSearch(queryText, null);
    }

    /**
     * 统一的检索入口，接口风格和 Milvus 示例保持一致。
     * 支持 DENSE_ONLY / SPARSE_ONLY / HYBRID 三种模式，
     * 同时兼容旧值 VECTOR_ONLY / KEYWORD_ONLY。
     */
    public List<SearchResultItem> search(String queryText, String mode) {
        return search(queryText, mode, null);
    }

    /**
     * 带分类过滤的统一检索入口。
     */
    public List<SearchResultItem> search(String queryText, String mode, String category) {
        return switch (normalizeMode(mode)) {
            case MODE_DENSE_ONLY -> vectorOnlySearch(queryText, category);
            case MODE_SPARSE_ONLY -> keywordOnlySearch(queryText, category);
            default -> hybridSearch(queryText, category);
        };
    }

    /**
     * 带可选分类过滤的混合检索。
     */
    public List<SearchResultItem> hybridSearch(String queryText, String category) {
        int vectorTopK = properties.getVectorTopK();
        int keywordTopK = properties.getKeywordTopK();
        int finalTopK = properties.getFinalTopK();
        boolean filterByCategory = StringUtils.hasText(category);

        // ===== 第一路：向量语义检索 =====
        // 通过 Spring AI VectorStore，查询文本会自动被 EmbeddingModel 转成向量
        // 然后在 PGVector 中按余弦相似度找最近的 vectorTopK 个文档
        List<Document> vectorDocs = filterByCategory
                ? vectorSearchService.searchRawDocumentsWithCategory(queryText, category, vectorTopK)
                : vectorSearchService.searchRawDocuments(queryText, vectorTopK);

        // ===== 第二路：ES 关键词检索 =====
        // 查询文本经过 IK 分词后，用 BM25 算法在 ES 倒排索引中匹配
        // 返回 BM25 得分最高的 keywordTopK 个文档
        List<SearchResultItem> keywordResults = filterByCategory
                ? esKeywordService.searchByKeywordWithCategory(queryText, category, keywordTopK)
                : esKeywordService.searchByKeyword(queryText, keywordTopK);

        // ===== 第三步：RRF 融合 =====
        List<SearchResultItem> fusedResults = rrfFusion(vectorDocs, keywordResults, finalTopK);

        log.info("混合检索完成: query={}, category={}, 向量召回={}, 关键词召回={}, 融合后={}",
                queryText, category, vectorDocs.size(), keywordResults.size(), fusedResults.size());

        return fusedResults;
    }

    /**
     * 纯向量检索（用于对比）。
     */
    public List<SearchResultItem> vectorOnlySearch(String queryText) {
        return vectorOnlySearch(queryText, null);
    }

    /**
     * 带分类过滤的纯向量检索。
     */
    public List<SearchResultItem> vectorOnlySearch(String queryText, String category) {
        return StringUtils.hasText(category)
                ? vectorSearchService.searchByVectorWithCategory(queryText, category, properties.getFinalTopK())
                : vectorSearchService.searchByVector(queryText, properties.getFinalTopK());
    }

    /**
     * 纯关键词检索（用于对比）。
     */
    public List<SearchResultItem> keywordOnlySearch(String queryText) {
        return keywordOnlySearch(queryText, null);
    }

    /**
     * 带分类过滤的纯关键词检索。
     */
    public List<SearchResultItem> keywordOnlySearch(String queryText, String category) {
        return StringUtils.hasText(category)
                ? esKeywordService.searchByKeywordWithCategory(queryText, category, properties.getFinalTopK())
                : esKeywordService.searchByKeyword(queryText, properties.getFinalTopK());
    }

    /**
     * 三种模式同时跑，返回对比结果。
     * <p>
     * 把同一个查询分别交给向量检索、关键词检索、混合检索，
     * 结果放在一个 Map 里返回，key 是模式名，value 是结果列表。
     * 方便直观对比三种模式的效果差异，特别是对精确关键词查询的表现。
     *
     * @param queryText 用户查询文本
     * @return Map: 模式名 → 结果列表
     */
    public Map<String, List<SearchResultItem>> compareSearch(String queryText) {
        return compareSearch(queryText, null);
    }

    /**
     * 带分类过滤的三模式对比。
     */
    public Map<String, List<SearchResultItem>> compareSearch(String queryText, String category) {
        Map<String, List<SearchResultItem>> result = new LinkedHashMap<>();
        result.put(MODE_DENSE_ONLY, search(queryText, MODE_DENSE_ONLY, category));
        result.put(MODE_SPARSE_ONLY, search(queryText, MODE_SPARSE_ONLY, category));
        result.put(MODE_HYBRID, search(queryText, MODE_HYBRID, category));
        return result;
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return MODE_HYBRID;
        }

        return switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "VECTOR_ONLY", MODE_DENSE_ONLY -> MODE_DENSE_ONLY;
            case "KEYWORD_ONLY", MODE_SPARSE_ONLY -> MODE_SPARSE_ONLY;
            case MODE_HYBRID -> MODE_HYBRID;
            default -> {
                log.warn("收到未知检索模式: {}，已自动回退到 HYBRID", mode);
                yield MODE_HYBRID;
            }
        };
    }

    /**
     * RRF（Reciprocal Rank Fusion）倒数排名融合算法。
     * <p>
     * 核心思想：不看分数只看排名。一个文档在两个排名列表中排名都靠前，
     * 那它的 RRF 分数就高，大概率是用户想找的文档。
     * <p>
     * 公式：RRF_score(d) = Σ 1/(K + rank_i(d))
     * <p>
     * K 是一个常数（默认 60），作用是防止排名第 1 的文档 RRF 分数远远高于排名第 2 的。
     * K 越大，不同排名之间的分数差距越小，融合结果越"平均"。
     * K=60 是学术界和工业界反复验证过的经验值，几乎不需要调。
     * <p>
     * 为什么不直接把两路的分数加起来？
     * 因为向量检索的分数是余弦相似度（0~1），BM25 的分数是无上界的正数（可能是 0.5 也可能是 15.8），
     * 两种分数量纲完全不同。直接相加会让 BM25 分数碾压向量分数，向量检索的语义判断等于白做了。
     * RRF 只用排名不用分数，天然回避了这个问题。
     *
     * @param vectorDocs     向量检索返回的 Spring AI Document 列表（已按相似度排序）
     * @param keywordResults 关键词检索返回的结果列表（已按 BM25 分数排序）
     * @param topK           融合后最终返回的文档数量
     * @return 按 RRF 分数降序排列的结果列表
     */
    private List<SearchResultItem> rrfFusion(List<Document> vectorDocs,
                                              List<SearchResultItem> keywordResults,
                                              int topK) {
        int K = properties.getRrfK();

        // rrfScores：文档 ID → 累加的 RRF 分数
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        // contentMap：文档 ID → 文档内容（用于构建最终结果）
        Map<String, SearchResultItem> contentMap = new HashMap<>();

        // ===== 计算向量检索结果的 RRF 分数 =====
        // vectorDocs 已经按余弦相似度降序排列，第 0 个是最相似的
        for (int rank = 0; rank < vectorDocs.size(); rank++) {
            Document doc = vectorDocs.get(rank);
            String docId = doc.getId();

            // RRF 分数 = 1 / (K + rank + 1)
            // rank 从 0 开始，但 RRF 公式里排名从 1 开始，所以要 +1
            double rrfScore = 1.0 / (K + rank + 1);
            rrfScores.merge(docId, rrfScore, Double::sum);

            // 记录文档内容，后面构建结果时用
            Map<String, Object> metadata = doc.getMetadata();
            contentMap.putIfAbsent(docId, SearchResultItem.builder()
                    .id(docId)
                    .title(metadata != null ? (String) metadata.get("title") : "")
                    .content(doc.getText())
                    .category(metadata != null ? (String) metadata.get("category") : "")
                    .mode("HYBRID")
                    .build());
        }

        // ===== 计算关键词检索结果的 RRF 分数 =====
        // keywordResults 已经按 BM25 分数降序排列
        for (int rank = 0; rank < keywordResults.size(); rank++) {
            SearchResultItem item = keywordResults.get(rank);
            String docId = item.getId();

            double rrfScore = 1.0 / (K + rank + 1);
            // merge：如果这个文档已经在 rrfScores 中（说明向量检索也命中了），
            // 就把两路的 RRF 分数加起来。这正是 RRF 的精髓——在两路中都排名靠前的文档分数最高
            rrfScores.merge(docId, rrfScore, Double::sum);

            // putIfAbsent：如果向量检索已经记录了这个文档的内容，就不覆盖
            contentMap.putIfAbsent(docId, SearchResultItem.builder()
                    .id(docId)
                    .title(item.getTitle())
                    .content(item.getContent())
                    .category(item.getCategory())
                    .mode("HYBRID")
                    .build());
        }

        // ===== 按 RRF 分数降序排列，取 Top-K =====
        return rrfScores.entrySet().stream()
                // 按 RRF 分数从高到低排序
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                // 只取前 topK 个
                .limit(topK)
                .map(entry -> {
                    SearchResultItem item = contentMap.get(entry.getKey());
                    // 把 RRF 融合分数设置到结果中
                    item.setScore(entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }
}
