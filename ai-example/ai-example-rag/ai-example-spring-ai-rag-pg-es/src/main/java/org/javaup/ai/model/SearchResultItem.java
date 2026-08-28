package org.javaup.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 检索结果项 —— 统一封装向量检索、关键词检索、混合检索的返回结果
 * @author: 阿星不是程序员
 **/
/**
 * 检索结果项 —— 统一封装向量检索、关键词检索、混合检索的返回结果。
 * <p>
 * 不管是哪种检索模式返回的结果，都包装成这个对象，
 * 方便前端统一展示和对比不同检索模式的效果差异。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {

    /**
     * 文档唯一标识（PGVector 和 ES 共用同一个 ID）
     */
    private String id;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容（文本块）
     */
    private String content;

    /**
     * 文档分类
     */
    private String category;

    /**
     * 检索得分
     * - 向量检索：余弦相似度（0~1）
     * - 关键词检索：BM25 分数（无上界正数）
     * - 混合检索：RRF 融合分数
     */
    private double score;

    /**
     * 检索模式标识，用于区分结果来源
     * 取值：DENSE_ONLY / SPARSE_ONLY / HYBRID
     */
    private String mode;
}
