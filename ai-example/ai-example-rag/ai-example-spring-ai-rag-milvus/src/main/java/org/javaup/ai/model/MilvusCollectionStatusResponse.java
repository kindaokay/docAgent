package org.javaup.ai.model;

import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 模型对象
 * @author: 阿星不是程序员
 **/
public record MilvusCollectionStatusResponse(
        String uri,
        String databaseName,
        String collectionName,
        boolean exists,
        String loadState,
        Map<String, String> statistics,
        List<MilvusIndexInfo> indexes,
        String collectionSummary
) {
}
