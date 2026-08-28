package org.javaup.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.Disease;
import org.javaup.ai.model.Drug;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class MedicalKnowledgeService {

    private final VectorStore vectorStore;

    public MedicalKnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 导入疾病知识
     */
    public void importDiseaseKnowledge(Disease disease) {
        String content = String.format(
                "疾病名称：%s\n症状：%s\n治疗方案：%s",
                disease.getName(),
                disease.getSymptoms(),
                disease.getTreatment()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "disease");
        metadata.put("department", disease.getDepartment());
        metadata.put("category", disease.getCategory());
        metadata.put("diseaseId", disease.getId());

        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
        log.info("导入疾病知识：{}", disease.getName());
    }

    /**
     * 导入药品知识
     */
    public void importDrugKnowledge(Drug drug) {
        String content = String.format(
                "药品名称：%s\n适应症：%s\n用法用量：%s\n注意事项：%s",
                drug.getName(),
                drug.getIndications(),
                drug.getDosage(),
                drug.getPrecautions()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "drug");
        metadata.put("category", drug.getCategory());
        metadata.put("drugId", drug.getId());

        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
        log.info("导入药品知识：{}", drug.getName());
    }
}
