package org.javaup.ai.split;

import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: Spring AI Alibaba 的递归分片实现
 * @author: 阿星不是程序员
 **/
/**
 * Spring AI Alibaba 的递归分片实现。
 *
 * 这里特意没有直接使用 splitter.apply(documents)：
 * 1. 我们希望过滤掉递归切分过程中产生的空 chunk；
 * 2. 我们希望自己补齐 parent_document_id / chunk_index / total_chunks 元数据，方便教学演示；
 * 3. 我们希望对代码块和 Mermaid 这类结构化内容做特殊处理，避免被切得过碎。
 */
public class SpringAiAlibabaRecursiveTextSplit {

    /**
     * 真正拿 Markdown 技术文档做 RAG 时， 500 更接近实战配置。
     */
    private static final int CHUNK_SIZE = 500;

    /**
     * 为了保留“语义完整的句子 / 段落”，这里有意不再按空格和逗号切。
     * 否则英文术语、缩写和代码标识符很容易被拆成 "Java"、"JVM"、"API" 这样的碎片。
     */
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", "；"};

    private SpringAiAlibabaRecursiveTextSplit() {
    }

    public static List<Document> split(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(CHUNK_SIZE, SEPARATORS);
        List<Document> result = new ArrayList<>();

        for (Document document : documents) {
            List<String> chunks = splitSingleDocument(document, splitter);
            if (CollectionUtils.isEmpty(chunks)) {
                continue;
            }

            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                metadata.put("parent_document_id", document.getId());
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", chunks.size());

                Document child = new Document(chunks.get(i), metadata);
                child.setContentFormatter(document.getContentFormatter());
                result.add(child);
            }
        }

        return result;
    }

    private static List<String> splitSingleDocument(Document document, RecursiveCharacterTextSplitter splitter) {
        String text = document.getText();
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }

        // 代码块 / Mermaid 块本身已经是结构化内容，演示时保留原块更容易理解。
        if (isCodeLikeDocument(document) || text.length() <= CHUNK_SIZE) {
            return List.of(text.trim());
        }

        return splitter.splitText(text).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static boolean isCodeLikeDocument(Document document) {
        Object category = document.getMetadata().get("category");
        return "code_block".equals(category) || document.getMetadata().containsKey("lang");
    }
}
