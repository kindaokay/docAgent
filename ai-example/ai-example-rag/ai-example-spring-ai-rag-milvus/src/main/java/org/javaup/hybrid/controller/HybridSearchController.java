package org.javaup.hybrid.controller;

import org.javaup.hybrid.model.HybridSearchResult;
import org.javaup.hybrid.service.HybridCollectionManager;
import org.javaup.hybrid.service.HybridSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * Milvus 原生混合检索的 REST 接口。
 * <p>
 * 提供三个核心端点：
 * <ul>
 *   <li>GET /hybrid/search —— 执行检索，通过 mode 参数切换 DENSE_ONLY / SPARSE_ONLY / HYBRID</li>
 *   <li>GET /hybrid/compare —— 同一个查询同时跑三种模式，方便对比效果差异</li>
 *   <li>POST /hybrid/init —— 手动触发 Collection 初始化（创建 Schema + 建索引 + 插入演示数据）</li>
 * </ul>
 * <p>
 * 使用方式：
 * 1. 先调用 POST /hybrid/init 初始化 Collection（或者在 yaml 里把 initialize-on-startup 改成 true）
 * 2. 然后调用 GET /hybrid/search?query=高血压怎么管理 进行检索
 * 3. 用 GET /hybrid/compare?query=二甲双胍 eGFR 禁忌 对比三种模式的效果
 */
@RestController
@RequestMapping("/hybrid")
public class HybridSearchController {

    private final HybridSearchService searchService;
    private final HybridCollectionManager collectionManager;

    public HybridSearchController(HybridSearchService searchService,
                                  HybridCollectionManager collectionManager) {
        this.searchService = searchService;
        this.collectionManager = collectionManager;
    }

    /**
     * 混合检索接口。
     * <p>
     * 示例请求：
     * - GET /hybrid/search?query=高血压日常怎么管理&mode=HYBRID
     * - GET /hybrid/search?query=二甲双胍 eGFR&mode=SPARSE_ONLY
     * - GET /hybrid/search?query=慢性病用药注意事项（不传 mode 默认走 HYBRID）
     *
     * @param query 用户的查询文本
     * @param mode  检索模式，默认 HYBRID。可选值：
     *              DENSE_ONLY —— 纯语义向量检索
     *              SPARSE_ONLY —— 纯 BM25 关键词检索
     *              HYBRID —— 两路并行 + RRF 融合（推荐）
     * @return 按相关性排序的检索结果列表
     */
    @GetMapping("/search")
    public List<HybridSearchResult> search(
            @RequestParam("query") String query,
            @RequestParam(name = "mode", defaultValue = "HYBRID") String mode) {
        return searchService.search(query, mode);
    }

    /**
     * 对比接口：同一个查询同时跑三种模式，返回一个 Map，key 是模式名，value 是结果列表。
     * <p>
     * 这个接口特别适合验证混合检索的价值。比如查"二甲双胍 eGFR 禁忌"：
     * - DENSE_ONLY 可能返回一堆"糖尿病用药"相关的文档，但不一定精确匹配到"eGFR"
     * - SPARSE_ONLY 能精确匹配"二甲双胍"和"eGFR"，但可能漏掉语义相关的内容
     * - HYBRID 两者兼顾，既能精确匹配关键词，又能召回语义相关的文档
     * <p>
     * 示例请求：GET /hybrid/compare?query=CPR 胸外按压频率
     *
     * @param query 用户的查询文本
     * @return key 为模式名（DENSE_ONLY / SPARSE_ONLY / HYBRID），value 为对应的检索结果
     */
    @GetMapping("/compare")
    public Map<String, List<HybridSearchResult>> compare(@RequestParam("query") String query) {
        return Map.of(
                "DENSE_ONLY", searchService.search(query, "DENSE_ONLY"),
                "SPARSE_ONLY", searchService.search(query, "SPARSE_ONLY"),
                "HYBRID", searchService.search(query, "HYBRID")
        );
    }

    /**
     * 手动初始化 Hybrid Collection。
     * <p>
     * 执行流程：
     * 1. 删除已有的 Hybrid Collection（如果存在的话）
     * 2. 重新创建 Collection（定义 Schema、注册 BM25 Function、创建索引）
     * 3. 把 Collection 加载到内存
     * 4. 插入一批医疗健康领域的演示数据
     * <p>
     * 什么时候需要调这个接口？
     * - 第一次使用时，需要先初始化才能检索
     * - 修改了 Schema 或索引参数后，需要重新初始化
     * - 演示数据被搞乱了，想重置成干净状态
     * <p>
     * 也可以不调这个接口，在 application.yaml 里把 initialize-on-startup 改成 true，
     * 这样每次启动应用时会自动初始化。
     * <p>
     * 示例请求：POST /hybrid/init
     */
    @PostMapping("/init")
    public Map<String, Object> init() {
        // 先删后建，确保是干净的状态
        collectionManager.dropCollectionIfExists();
        collectionManager.createCollection();
        collectionManager.loadCollection();
        long count = collectionManager.insertDemoData();
        return Map.of(
                "status", "ok",
                "message", "Hybrid Collection 初始化完成",
                "insertedCount", count
        );
    }
}
