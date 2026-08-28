package org.javaup.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 配置属性
 * @author: 阿星不是程序员
 **/
/**
 * 混合检索的配置属性类。
 * <p>
 * 对应 application.yaml 中 hybrid.search 前缀下的所有配置项。
 * 把检索相关的参数集中管理，方便统一调整，不用到处改硬编码的魔法数字。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hybrid.search")
public class HybridSearchProperties {

    /**
     * 启动时是否自动重建并导入演示数据。
     * <p>
     * 默认关闭，避免每次启动都把本地调试数据清掉。
     * 如果想让示例“一启动就能搜”，可以改成 true。
     */
    private boolean initializeOnStartup = true;

    /**
     * 自动初始化前是否先清空旧数据。
     * 对示例项目来说通常建议开启，避免重复导入造成结果干扰。
     */
    private boolean resetOnStartup = true;

    /**
     * ES 索引名称，默认 tech_knowledge
     */
    private String esIndexName = "tech_knowledge";

    /**
     * ES 索引时使用的分词器
     * ik_max_word：最细粒度分词，索引时用，保证召回率
     * 如果没装 IK 插件，改成 standard
     */
    private String esAnalyzer = "ik_max_word";

    /**
     * ES 搜索时使用的分词器
     * ik_smart：智能分词，搜索时用，保证准确率
     * 如果没装 IK 插件，改成 standard
     */
    private String esSearchAnalyzer = "ik_smart";

    /**
     * 向量检索的召回数量
     * 设大一点保证召回率，后面靠 RRF 融合筛选
     */
    private int vectorTopK = 20;

    /**
     * 关键词检索的召回数量
     * 和向量检索保持一致
     */
    private int keywordTopK = 20;

    /**
     * 向量检索的相似度阈值
     * 低于这个分数的结果直接丢弃，初始设低一点保证召回
     */
    private double similarityThreshold = 0.3;

    /**
     * RRF 融合算法的常数 K
     * 公式：RRF_score = Σ 1/(K + rank)
     * K=60 是经验值，几乎不需要调
     */
    private int rrfK = 60;

    /**
     * 融合后最终返回的文档数量
     */
    private int finalTopK = 5;

    /**
     * PGVector 使用的底层表名。
     * Spring AI Starter 默认会创建 public.vector_store，所以这里也沿用这个默认值。
     */
    private String vectorTableName = "vector_store";
}
