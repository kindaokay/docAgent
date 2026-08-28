package org.javaup.hybrid.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 配置属性
 * @author: 阿星不是程序员
 **/
/**
 * Milvus 原生混合检索的配置项，对应 application.yaml 中 app.hybrid.milvus 前缀。
 * <p>
 * 混合检索需要在同一个 Collection 里同时维护 Dense 向量字段和 Sparse 向量字段，
 * 因此这里把 Schema 定义、索引参数、检索参数都集中到一个配置类里，
 * 避免散落在多个地方导致维度、TopK 等关键值不一致。
 */
@Data
@ConfigurationProperties(prefix = "app.hybrid.milvus")
public class HybridMilvusProperties {

    /** 启动时是否自动创建 Hybrid Collection 并导入演示数据 */
    private boolean initializeOnStartup = true;

    /** 初始化前是否先删除已有 Collection，适合反复调试的场景 */
    private boolean resetBeforeImport = true;

    /** Hybrid Collection 名称，和 Spring AI 默认的 VectorStore Collection 分开 */
    private String collectionName = "knowledge_chunks_hybrid";

    /** text 字段最大长度（字符数），BM25 分词器会对这个字段做 analyze */
    private int maxTextLength = 8192;

    // ========== Dense 向量相关 ==========

    /** Dense 向量维度，必须和 Embedding 模型输出维度一致 */
    private int denseDimension = 4096;

    /** HNSW 索引的 M 参数：每个节点最多保留多少条边 */
    private int denseIndexM = 16;

    /** HNSW 索引的 efConstruction 参数：建索引时的搜索宽度 */
    private int denseIndexEfConstruction = 256;

    /** Dense 检索时的 ef 参数：值越大召回越稳，但查询越慢 */
    private int denseSearchEf = 64;

    /** Dense 分支的召回数量 */
    private int denseRecallTopK = 12;

    // ========== Sparse（BM25）相关 ==========

    /** Sparse 分支的召回数量 */
    private int sparseRecallTopK = 12;

    /** BM25 检索时的长尾剪枝比例，过滤掉得分过低的稀疏匹配项 */
    private double dropRatioSearch = 0.15;

    // ========== 融合与最终输出 ==========

    /** RRF 融合公式中的 K 值，一般取 60 即可 */
    private int rrfK = 60;

    /** 融合后最终返回的文档数量 */
    private int finalTopK = 5;
}
