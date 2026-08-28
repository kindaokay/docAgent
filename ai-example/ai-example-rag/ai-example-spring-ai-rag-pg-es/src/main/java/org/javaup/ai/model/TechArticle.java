package org.javaup.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 技术文章模型 —— 用于导入知识库时的输入对象
 * @author: 阿星不是程序员
 **/
/**
 * 技术文章模型 —— 用于导入知识库时的输入对象。
 * <p>
 * 每篇文章会被同时写入 PGVector（向量检索）和 Elasticsearch（关键词检索），
 * 写入时使用相同的 chunkId，方便 RRF 融合时跨系统去重。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechArticle {

    /**
     * 文章唯一 ID。
     * <p>
     * 这是双系统方案里非常关键的字段：
     * - 会先转换成 PGVector 可接受的 UUID 形式，作为 Document.id
     * - Elasticsearch 会复用同一个最终 ID 作为文档的 _id
     * <p>
     * 如果调用方不传，示例代码会自动生成一个 UUID。
     * 如果调用方传的是普通业务编码，比如 "redis-7-io-model"，
     * 也会被稳定映射成同一个 UUID，不需要手动改成 UUID。
     */
    private String id;

    /**
     * 文章标题，比如"Kubernetes Pod 生命周期与探针配置"
     */
    private String title;

    /**
     * 文章正文内容（已经切好的文本块）
     * 实际项目中这里通常是经过 TextSplitter 切分后的 chunk，
     * 为了演示简单，这里直接当作一整块文本
     */
    private String content;

    /**
     * 文章分类，比如 "container"、"database"、"framework"、"devops"
     * 用于元数据过滤，支持按分类缩小检索范围
     */
    private String category;

    /**
     * 标签列表，比如 ["kubernetes", "pod", "health-check"]
     * 存入 ES 的 keyword 字段，支持精确过滤
     */
    private List<String> tags;
}
