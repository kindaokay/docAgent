package org.javaup.ai.test;

import org.springframework.ai.document.Document;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 生成器
 * @author: 阿星不是程序员
 **/
public class CitationGenerator {

    /**
     * 从 chunk 元数据生成引用信息
     */
    public static String generateCitation(Document chunk) {
        Map<String, Object> meta = chunk.getMetadata();
        
        StringBuilder citation = new StringBuilder();
        citation.append("**依据**: ");
        
        // 文档名
        String fileName = (String) meta.get("file_name");
        if (fileName != null) {
            citation.append("《").append(fileName.replace(".pdf", "")).append("》");
        }
        
        // 章节路径
        String sectionPath = (String) meta.get("section_path");
        if (sectionPath != null) {
            citation.append(" ").append(sectionPath);
        }
        
        // 页码
        Object pageNumber = meta.get("page_number");
        if (pageNumber instanceof Number number) {
            citation.append("，第").append(number.intValue()).append("页");
        }
        
        // 原文链接
        String sourceUrl = (String) meta.get("source_url");
        if (sourceUrl != null) {
            citation.append("\n\n[查看原文](").append(sourceUrl).append(")");
        }
        
        return citation.toString();
    }

    public static void main(String[] args) {
        // 模拟检索到的 chunk
        Map<String, Object> metadata = Map.of(
            "file_name", "药物相互作用手册.pdf",
            "section_path", "第四章 抗生素与解热镇痛药 > 4.2节",
            "page_number", 38,
            "source_url", "https://med.example.com/interaction/antibiotics.pdf#page=38"
        );
        
        Document chunk = new Document(
            "布洛芬与头孢菌素类抗生素无明显相互作用，一般情况下可同时使用...",
            metadata
        );
        
        String answer = chunk.getText();
        String citation = generateCitation(chunk);
        
        System.out.println("回答: " + answer);
        System.out.println();
        System.out.println(citation);
    }
}
