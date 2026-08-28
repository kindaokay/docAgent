package org.javaup.ai.test;

import org.springframework.ai.document.Document;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 业务类
 * @author: 阿星不是程序员
 **/
public class PermissionFilter {

    /**
     * 模拟知识库中的文本块
     */
    private static List<Document> mockMedicalChunks() {
        List<Document> chunks = new ArrayList<>();

        // 块一：公开的健康科普
        chunks.add(new Document(
            "感冒是由病毒引起的上呼吸道感染，一般5-7天可自愈...",
            Map.of(
                "access_level", "public",
                "access_roles", Arrays.asList("patient", "doctor", "pharmacist"),
                "applicable_group", "all"
            )
        ));

        // 块二：医生专用的诊疗指南
        chunks.add(new Document(
            "抗生素应根据药敏试验结果选择，首选青霉素类...",
            Map.of(
                "access_level", "internal",
                "access_roles", Arrays.asList("doctor"),
                "applicable_group", "all"
            )
        ));

        // 块三：儿童用药指南
        chunks.add(new Document(
            "6岁以下儿童使用布洛芬，剧量按体重计算，每次5-10mg/kg...",
            Map.of(
                "access_level", "public",
                "access_roles", Arrays.asList("patient", "doctor", "pharmacist"),
                "applicable_group", "child"
            )
        ));

        // 块四：成人用药指南
        chunks.add(new Document(
            "成人布洛芬常规剧量：每次400mg，每日3次...",
            Map.of(
                "access_level", "public",
                "access_roles", Arrays.asList("patient", "doctor", "pharmacist"),
                "applicable_group", "adult"
            )
        ));

        return chunks;
    }

    /**
     * 根据用户身份过滤文本块
     */
    public static List<Document> filterByPermission(
            List<Document> chunks,
            String userRole,
            String queryGroup) {
        
        return chunks.stream()
            .filter(chunk -> hasPermission(chunk, userRole, queryGroup))
            .collect(Collectors.toList());
    }

    private static boolean hasPermission(Document chunk, String userRole, String queryGroup) {
        Map<String, Object> meta = chunk.getMetadata();
        
        // 检查角色权限
        @SuppressWarnings("unchecked")
        List<String> accessRoles = (List<String>) meta.get("access_roles");
        if (accessRoles == null || !accessRoles.contains(userRole)) {
            return false;
        }
        
        // 检查适用人群
        String applicableGroup = (String) meta.get("applicable_group");
        if (!"all".equals(applicableGroup) && !applicableGroup.equals(queryGroup)) {
            return false;
        }
        
        return true;
    }

    private static String previewText(Document chunk, int maxLength) {
        String content = chunk.getText();
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength).replaceAll("\\.+$", "") + "...";
    }

    public static void main(String[] args) {
        List<Document> allChunks = mockMedicalChunks();

        // 场景1：普通患者查询成人用药信息
        System.out.println("=== 患者查询成人用药信息 ===");
        List<Document> patientAdultResults = filterByPermission(allChunks, "patient", "adult");
        patientAdultResults.forEach(chunk -> 
            System.out.println("- " + previewText(chunk, 31))
        );
        
        System.out.println();

        // 场景2：医生查询儿童用药信息
        System.out.println("=== 医生查询儿童用药信息 ===");
        List<Document> doctorChildResults = filterByPermission(allChunks, "doctor", "child");
        doctorChildResults.forEach(chunk -> 
            System.out.println("- " + previewText(chunk, 31))
        );
    }
}
