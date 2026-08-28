package org.javaup.ai.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 模型对象
 * @author: 阿星不是程序员
 **/
public record MilvusChunkRequest(
        @NotBlank(message = "chunk id 不能为空")
        String id,
        @NotBlank(message = "content 不能为空")
        String content,
        @NotBlank(message = "docId 不能为空")
        String docId,
        @NotBlank(message = "category 不能为空")
        String category,
        String source,
        Map<String, Object> metadata
) {
}
