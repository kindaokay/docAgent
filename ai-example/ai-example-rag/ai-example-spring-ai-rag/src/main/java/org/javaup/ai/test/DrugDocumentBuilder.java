package org.javaup.ai.test;

import org.springframework.ai.document.Document;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 构建器
 * @author: 阿星不是程序员
 **/
public class DrugDocumentBuilder {

    /**
     * 创建带身份信息的药品文本块
     */
    public static Document createDrugChunk(String content, String drugId, 
                                            String drugName, String sourceUrl) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_id", "drug_" + drugId);
        metadata.put("file_name", drugName + "说明书.pdf");
        metadata.put("source_url", sourceUrl);
        
        return new Document(content, metadata);
    }

    public static void main(String[] args) {
        String content = "布洛芬为非甾体抗炎药，具有解热、镇痛、抗炎作用。";
        
        Document chunk = createDrugChunk(
            content,
            "ibuprofen_001",
            "布洛芬",
            "https://med.example.com/drugs/ibuprofen.pdf"
        );
        
        System.out.println("内容: " + chunk.getText());
        System.out.println("文档ID: " + chunk.getMetadata().get("doc_id"));
        System.out.println("来源: " + chunk.getMetadata().get("source_url"));
    }
}
