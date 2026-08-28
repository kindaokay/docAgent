package org.javaup.ai.service;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.EsArticleDocument;
import org.javaup.ai.model.TechArticle;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 知识库导入服务 —— 负责把技术文章同时写入 PGVector 和 Elasticsearch。
 * <p>
 * 混合检索依赖两套存储系统：
 * - PGVector：存向量，用于语义检索
 * - Elasticsearch：存原文，用于关键词检索（BM25）
 * <p>
 * 导入时要保证"双写一致"——同一篇文章在两个系统里用同一个 ID。
 * 这样 RRF 融合时才能正确识别两路检索命中的是不是同一篇文档。
 * <p>
 * 双写一致性保障策略：
 * 1. 先写 PGVector（Spring AI 自动生成 Document ID）
 * 2. 拿到 ID 后再写 ES（用同一个 ID 作为 ES 文档的 _id）
 * 3. 如果 ES 写入失败，PGVector 中的文档仍然可用（降级为纯向量检索）
 * <p>
 * 生产环境中如果对一致性要求更高，可以引入事务消息或 CDC（Change Data Capture）方案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeImportService {

    private final VectorSearchService vectorSearchService;
    private final ElasticsearchKeywordService esKeywordService;

    /**
     * 导入单篇技术文章到知识库（双写 PGVector + ES）。
     * <p>
     * 整个流程：
     * 1. 把文章内容包装成 Spring AI 的 Document 对象，设置 metadata
     * 2. 通过 VectorStore 写入 PGVector（内部自动调用 EmbeddingModel 生成向量）
     * 3. 用相同的 Document ID 写入 ES（建立倒排索引供 BM25 检索使用）
     *
     * @param article 技术文章
     * @return 写入成功后的 Document ID
     */
    public String importArticle(TechArticle article) {
        String docId = resolveDocumentId(article);

        // ===== 第一步：构建 Spring AI Document =====
        // 把标题和正文拼成一段完整的文本，作为 Document 的内容
        // 这样向量检索时，标题信息也会被编码到向量里
        String fullText = buildFullText(article);

        // metadata 里存文章的结构化属性，用于检索时的过滤和结果展示
        Map<String, Object> metadata = buildMetadata(article);

        // 这里显式指定文档 ID，而不是交给 Spring AI 自动生成，
        // 这样 PGVector 和 ES 才能稳定共用同一个主键，后续做重建、更新、RRF 融合都更直观。
        Document document = new Document(docId, fullText, metadata);

        // ===== 第二步：写入 PGVector =====
        // vectorStore.add() 内部会：
        // 1. 调用 EmbeddingModel 把 fullText 转成 1024 维向量
        // 2. 执行 SQL INSERT 把文本、向量、metadata 写入 vector_store 表
        vectorSearchService.addDocuments(List.of(document));

        // ===== 第三步：写入 Elasticsearch =====
        // 用同一个 docId 作为 ES 文档的 _id
        // ES 会对 title 和 content 做分词，建立倒排索引
        try {
            esKeywordService.indexDocument(toEsDocument(article, docId));
        } catch (Exception e) {
            // ES 写入失败不影响 PGVector 中已写入的数据
            // 降级为纯向量检索，不会导致数据完全丢失
            log.warn("文章已写入 PGVector 但 ES 写入失败: id={}, title={}, 原因: {}",
                    docId, article.getTitle(), e.getMessage());
        }

        log.info("文章导入成功: id={}, title={}, category={}",
                docId, article.getTitle(), article.getCategory());
        return docId;
    }

    /**
     * 批量导入多篇技术文章。
     * <p>
     * 批量写入比逐条写入效率高，特别是 PGVector 端，
     * Spring AI 会把多个文档的 embedding 请求合并成一次 API 调用。
     *
     * @param articles 技术文章列表
     * @return 写入成功的 Document ID 列表
     */
    public List<String> importArticles(List<TechArticle> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return List.of();
        }

        List<Document> documents = new ArrayList<>();
        List<EsArticleDocument> esDocuments = new ArrayList<>();

        // 先构建所有 Document 对象，统一设置 metadata
        for (TechArticle article : articles) {
            String docId = resolveDocumentId(article);
            String fullText = buildFullText(article);

            Map<String, Object> metadata = buildMetadata(article);
            Document document = new Document(docId, fullText, metadata);
            documents.add(document);

            // 同时准备 ES 批量写入的数据，保持两个系统的主键完全一致
            esDocuments.add(toEsDocument(article, docId));
        }

        // 批量写入 PGVector
        vectorSearchService.addDocuments(documents);

        // 批量写入 ES
        try {
            esKeywordService.bulkIndex(esDocuments);
        } catch (Exception e) {
            log.warn("批量写入 ES 部分或全部失败，PGVector 数据不受影响: {}", e.getMessage());
        }

        List<String> ids = documents.stream()
                .map(Document::getId)
                .toList();
        log.info("批量导入完成: 共 {} 篇文章", ids.size());
        return ids;
    }

    /**
     * 把标题和正文拼成一段完整文本。
     * <p>
     * 格式：标题\n\n正文
     * 标题在前面可以让 embedding 向量更好地捕捉文章的主题信息。
     */
    private String buildFullText(TechArticle article) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(article.getTitle())) {
            sb.append(article.getTitle()).append("\n\n");
        }
        sb.append(article.getContent() != null ? article.getContent() : "");
        return sb.toString();
    }

    /**
     * 把业务对象里的结构化字段整理成向量库 metadata。
     * <p>
     * metadata 一方面用于结果展示，另一方面也方便后面做 category 等过滤检索。
     */
    private Map<String, Object> buildMetadata(TechArticle article) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", article.getTitle());
        metadata.put("category", article.getCategory());
        if (!CollectionUtils.isEmpty(article.getTags())) {
            metadata.put("tags", String.join(",", article.getTags()));
        }
        return metadata;
    }

    /**
     * 组装 ES 文档对象。
     */
    private EsArticleDocument toEsDocument(TechArticle article, String docId) {
        return EsArticleDocument.builder()
                .chunkId(docId)
                .title(article.getTitle())
                .content(article.getContent())
                .category(article.getCategory())
                .tags(article.getTags() != null ? article.getTags() : List.of())
                .build();
    }

    /**
     * 如果调用方没有传业务主键，就自动生成一个。
     * <p>
     * 这里用显式主键而不是随机让 Spring AI 自己生成，
     * 主要是为了让双系统示例在“导入、重置、重新初始化”这些动作上更可控。
     */
    private String resolveDocumentId(TechArticle article) {
        if (!StringUtils.hasText(article.getId())) {
            return IdUtil.fastSimpleUUID();
        }

        String rawId = article.getId().trim();

        // PGVector 在当前配置下要求主键必须能解析成 UUID。
        // 所以如果业务侧传的是 "redis-7-io-model" 这类可读字符串，
        // 这里统一做一次稳定映射，避免启动或导入时报 Invalid UUID string。
        if (isUuid(rawId)) {
            return rawId;
        }

        return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
