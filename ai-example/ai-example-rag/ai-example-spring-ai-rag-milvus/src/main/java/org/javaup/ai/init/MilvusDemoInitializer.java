package org.javaup.ai.init;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.config.MilvusDemoProperties;
import org.javaup.ai.service.MilvusAdminService;
import org.javaup.ai.service.MilvusKnowledgeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 初始化器
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class MilvusDemoInitializer implements CommandLineRunner {

    private final MilvusDemoProperties demoProperties;
    private final MilvusAdminService milvusAdminService;
    private final MilvusKnowledgeService milvusKnowledgeService;

    public MilvusDemoInitializer(MilvusDemoProperties demoProperties,
                                 MilvusAdminService milvusAdminService,
                                 MilvusKnowledgeService milvusKnowledgeService) {
        this.demoProperties = demoProperties;
        this.milvusAdminService = milvusAdminService;
        this.milvusKnowledgeService = milvusKnowledgeService;
    }

    @Override
    public void run(String... args) {
        if (!demoProperties.isInitializeOnStartup()) {
            log.info("Milvus demo 自动初始化已关闭");
            return;
        }

        // demo 默认做成“开箱即跑”，启动后就能直接搜索到样例数据。
        if (demoProperties.isResetBeforeImport()) {
            milvusAdminService.recreateCollection();
        }
        int size = milvusKnowledgeService.importDemoDocuments();
        log.info("Milvus demo 初始化完成，导入 {} 条演示数据", size);
    }
}
