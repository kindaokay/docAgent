package org.javaup.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class MedicalSearchService {

    private final VectorStore vectorStore;

    public MedicalSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 通用检索
     */
    public List<Document> search(String query, int topK) {
        log.info("通用检索，query={}, topK={}", query, topK);
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.4)
                        .build()
        );
    }

    /**
     * 按科室检索（只搜索特定科室的知识）
     */
    public List<Document> searchByDepartment(String query, String department, int topK) {
        log.info("按科室检索，query={}, department={}, topK={}", query, department, topK);
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.4)
                        .filterExpression(builder.eq("department", department).build())
                        .build()
        );
    }

    /**
     * 只搜索药品知识
     */
    public List<Document> searchDrugs(String query, int topK) {
        log.info("药品检索，query={}, topK={}", query, topK);
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(builder.eq("type", "drug").build())
                        .build()
        );
    }
}
