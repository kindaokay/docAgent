package org.javaup.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 配置属性
 * @author: 阿星不是程序员
 **/
@Data
@ConfigurationProperties(prefix = "app.milvus.demo")
public class MilvusDemoProperties {

    /**
     * 启动时是否自动导入演示数据，方便直接跑通 demo。
     */
    private boolean initializeOnStartup = true;

    /**
     * 自动导入前是否先重建 Collection，避免重复写入同一批样例数据。
     */
    private boolean resetBeforeImport = true;

    /**
     * 默认返回的 TopK 数量。
     */
    private int topK = 5;

    /**
     * 默认相似度阈值。
     */
    private double similarityThreshold = 0.15D;

    /**
     * HNSW 检索时的 ef 参数，值越大通常召回更好，但检索开销也更高。
     */
    private int defaultEf = 64;
}
