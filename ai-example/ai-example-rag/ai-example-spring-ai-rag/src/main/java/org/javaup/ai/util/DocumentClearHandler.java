package org.javaup.ai.util;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 工具类
 * @author: 阿星不是程序员
 **/
public class DocumentClearHandler {

    private DocumentClearHandler() {
    }

    /**
     * TokenTextSplitter 和自定义 OverlapParagraphTextSplit 的清洗方式。
     * 这两种分片更偏向“固定大小切块”，所以可以把空白统一压缩成一行文本。
     */
    public static List<Document> clearDocumentsForFlatSplit(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return documents;
        }

        return documents.stream()
                .map(doc -> {
                    if (doc == null || doc.getText() == null) {
                        return doc;
                    }

                    String text = doc.getText();
                    text = text.replaceAll("\\s+", " ").trim();
                    text = text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "");

                    return new Document(text, doc.getMetadata());
                })
                .collect(Collectors.toList());
    }

    /**
     * Spring AI Alibaba 递归分片的清洗方式。
     *
     * 递归分片依赖换行、段落和句号这些“结构化分隔符”来寻找自然边界，
     * 所以这里不能像固定大小分片那样把所有空白都压成一行。
     */
    public static List<Document> clearDocumentsForRecursiveSplit(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return documents;
        }

        return documents.stream()
                .map(doc -> {
                    if (doc == null || doc.getText() == null) {
                        return doc;
                    }

                    String text = isCodeLikeDocument(doc)
                            ? cleanCodeLikeDocument(doc.getText())
                            : cleanTextForRecursiveSplit(doc.getText());

                    return new Document(text, doc.getMetadata());
                })
                .collect(Collectors.toList());
    }

    /**
     * 为了兼容之前已经调用 clearDocuments 的代码，默认仍然返回“递归分片友好”的清洗结果。
     * 教学演示时，建议显式调用 clearDocumentsForFlatSplit / clearDocumentsForRecursiveSplit。
     */
    public static List<Document> clearDocuments(List<Document> documents) {
        return clearDocumentsForRecursiveSplit(documents);
    }

    private static String cleanTextForRecursiveSplit(String text) {
        // 1. 统一换行符，保留段落边界
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");

        // 2. 只压缩“行内空白”，不要破坏换行和段落
        normalized = normalized.replaceAll("[\\t\\x0B\\f ]+", " ");
        normalized = normalized.replaceAll(" *\\n *", "\n");

        // 3. 最多保留一个空行，避免文档中连续很多空行导致空 chunk
        normalized = normalized.replaceAll("\\n{3,}", "\n\n").trim();

        // 4. 去掉无意义的控制字符，但保留换行
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "");

        // 5. 按段落去重，并保留双换行，给递归分片留出优先级最高的切分点
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        Set<String> seen = new LinkedHashSet<>();
        List<String> orderedParagraphs = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (StringUtils.hasText(trimmed) && seen.add(trimmed)) {
                orderedParagraphs.add(trimmed);
            }
        }

        return String.join("\n\n", orderedParagraphs);
    }

    private static String cleanCodeLikeDocument(String text) {
        // 代码块 / Mermaid 更适合保留原始换行和缩进，否则会影响可读性与演示效果。
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n").trim();
        return normalized;
    }

    private static boolean isCodeLikeDocument(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        Object category = metadata.get("category");
        return "code_block".equals(category) || metadata.containsKey("lang");
    }
}
