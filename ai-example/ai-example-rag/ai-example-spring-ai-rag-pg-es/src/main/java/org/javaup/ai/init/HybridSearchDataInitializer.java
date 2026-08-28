package org.javaup.ai.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.config.HybridSearchProperties;
import org.javaup.ai.service.KnowledgeBaseAdminService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 初始化器
 * @author: 阿星不是程序员
 **/
/**
 * 应用启动时的演示数据初始化器。
 * <p>
 * 默认关闭，避免每次启动都重置数据。
 * 当你准备录制演示、写文档截图，或者想快速体验完整链路时，
 * 只要把 hybrid.search.initialize-on-startup 改成 true，就能自动把演示数据灌进去。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridSearchDataInitializer implements CommandLineRunner {

    private final HybridSearchProperties properties;
    private final KnowledgeBaseAdminService knowledgeBaseAdminService;

    @Override
    public void run(String... args) {
        if (!properties.isInitializeOnStartup()) {
            log.info("Hybrid PG+ES 演示数据自动初始化已关闭，如需开启请设置 hybrid.search.initialize-on-startup=true");
            return;
        }

        if (!properties.isResetOnStartup()) {
            log.info("检测到 initialize-on-startup=true 但 reset-on-startup=false，本示例仍按重建方式导入演示数据");
        }

        log.info("开始初始化 PGVector + Elasticsearch 演示知识库...");
        knowledgeBaseAdminService.rebuildDemoKnowledgeBase();
        log.info("PGVector + Elasticsearch 演示知识库初始化完成");
    }
}
