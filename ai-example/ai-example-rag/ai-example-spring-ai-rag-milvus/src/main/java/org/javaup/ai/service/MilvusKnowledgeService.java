package org.javaup.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.MilvusChunkRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class MilvusKnowledgeService {

    private static final String DEFAULT_SOURCE = "manual";

    private final VectorStore vectorStore;

    public MilvusKnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 使用文档章节里的典型业务语料构造一批固定 chunk，方便直接验证召回效果。
     */
    public int importDemoDocuments() {
        List<MilvusChunkRequest> demoChunks = List.of(
                new MilvusChunkRequest(
                        "chunk_hr_001_01",
                        "员工入职需要准备身份证原件、学历证书、离职证明、银行卡复印件和一寸照片，所有材料需在报到当天提交人力资源部。",
                        "hr_001",
                        "入职指南",
                        "员工手册V1",
                        Map.of("scene", "onboarding", "department", "HR")
                ),
                new MilvusChunkRequest(
                        "chunk_hr_001_02",
                        "年假按工龄计算：1到10年每年5天，10到20年每年10天，20年以上每年15天，未休年假按公司制度统一结转。",
                        "hr_001",
                        "假期政策",
                        "员工手册V1",
                        Map.of("scene", "leave-policy", "department", "HR")
                ),
                new MilvusChunkRequest(
                        "chunk_finance_001_01",
                        "报销流程是填写报销单后提交部门主管审批，再由财务审核并在每周四统一打款，发票抬头必须与公司名称完全一致。",
                        "finance_001",
                        "报销制度",
                        "财务制度V3",
                        Map.of("scene", "expense-reimbursement", "department", "Finance")
                )
        );
        return importChunks(demoChunks);
    }

    /**
     * Spring AI 会在 add 时自动调用 embedding 模型，把文本转为向量后写入 Milvus。
     */
    public int importChunks(List<MilvusChunkRequest> requests) {
        if (CollectionUtils.isEmpty(requests)) {
            return 0;
        }
        List<Document> documents = requests.stream()
                .map(this::toDocument)
                .toList();
        vectorStore.add(documents);
        log.info("Milvus demo 已写入 {} 条文档块", documents.size());
        return documents.size();
    }

    public void importChunk(MilvusChunkRequest request) {
        vectorStore.add(List.of(toDocument(request)));
        log.info("已写入文档块，chunkId={}, docId={}", request.id(), request.docId());
    }

    public void deleteByChunkId(String chunkId) {
        vectorStore.delete(List.of(chunkId));
        log.info("已删除文档块，chunkId={}", chunkId);
    }

    public void deleteByDocId(String docId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        vectorStore.delete(builder.eq("docId", docId).build());
        log.info("已按 docId 删除文档块，docId={}", docId);
    }

    private Document toDocument(MilvusChunkRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        // category / docId 放在 metadata 中，后续可直接走 Milvus 的 metadata 过滤能力。
        metadata.put("docId", request.docId());
        metadata.put("category", request.category());
        metadata.put("source", StringUtils.hasText(request.source()) ? request.source() : DEFAULT_SOURCE);
        if (!CollectionUtils.isEmpty(request.metadata())) {
            metadata.putAll(request.metadata());
        }

        return Document.builder()
                // 当前 demo 使用显式 chunkId，而不是 Milvus auto-id，方便删除、重建和文档更新。
                .id(request.id())
                .text(request.content())
                .metadata(metadata)
                .build();
    }
}
