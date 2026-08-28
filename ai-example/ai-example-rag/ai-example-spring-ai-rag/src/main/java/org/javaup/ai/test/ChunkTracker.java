package org.javaup.ai.test;

import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 业务类
 * @author: 阿星不是程序员
 **/
public class ChunkTracker {

    /**
     * 根据关键词查找可疑 chunks
     */
    public static List<Document> findSuspiciousChunks(List<Document> allChunks, String keyword) {
        return allChunks.stream()
            .filter(chunk -> chunk.getText().contains(keyword))
            .collect(Collectors.toList());
    }

    /**
     * 展示 chunk 详情，方便人工审核
     */
    public static void displayChunkDetails(Document chunk) {
        Map<String, Object> meta = chunk.getMetadata();
        
        System.out.println("========== Chunk 详情 ==========");
        System.out.println("内容: " + chunk.getText());
        System.out.println("文档ID: " + meta.get("doc_id"));
        System.out.println("文件名: " + meta.get("file_name"));
        System.out.println("Chunk序号: " + meta.get("chunk_index"));
        System.out.println("原文位置: " + meta.get("start_offset") + " - " + meta.get("end_offset"));
        System.out.println("入库时间: " + meta.get("created_at"));
        System.out.println("来源链接: " + meta.get("source_url"));
        System.out.println();
    }

    /**
     * 根据 doc_id 批量查找同文档的所有 chunks
     */
    public static List<Document> findChunksByDocId(List<Document> allChunks, String docId) {
        return allChunks.stream()
            .filter(chunk -> docId.equals(chunk.getMetadata().get("doc_id")))
            .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        // 模拟知识库
        List<Document> allChunks = Arrays.asList(
            new Document(
                "布洛芬每日最大剧量1600mg（旧版）...",
                Map.of(
                    "doc_id", "drug_ibuprofen_v1",
                    "file_name", "布洛芬说明书_旧版.pdf",
                    "chunk_index", 2,
                    "start_offset", 120,
                    "end_offset", 180,
                    "created_at", "2023-06-01T10:00:00Z",
                    "source_url", "https://med.example.com/drugs/ibuprofen_v1.pdf"
                )
            ),
            new Document(
                "布洛芬每日最大剧量1200mg（新版）...",
                Map.of(
                    "doc_id", "drug_ibuprofen_v2",
                    "file_name", "布洛芬说明书_新版.pdf",
                    "chunk_index", 2,
                    "start_offset", 150,
                    "end_offset", 210,
                    "created_at", "2024-03-15T14:00:00Z",
                    "source_url", "https://med.example.com/drugs/ibuprofen_v2.pdf"
                )
            )
        );

        // 步骤1：根据关键词查找可疑 chunks
        System.out.println(">>> 用户反馈：布洛芬剧量信息可能过时\n");
        List<Document> suspicious = findSuspiciousChunks(allChunks, "布洛芬");
        System.out.println("找到 " + suspicious.size() + " 个相关 chunks:\n");

        // 步骤2：展示详情
        suspicious.forEach(ChunkTracker::displayChunkDetails);

        // 步骤3：确认旧版需要删除
        System.out.println(">>> 确认 doc_id=drug_ibuprofen_v1 的内容已过时");
        System.out.println(">>> 执行删除操作...");
        System.out.println(">>> 删除完成，共删除 1 个 chunk");
    }
}