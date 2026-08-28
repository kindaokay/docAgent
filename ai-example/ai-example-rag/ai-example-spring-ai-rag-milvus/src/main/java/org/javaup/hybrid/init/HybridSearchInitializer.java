package org.javaup.hybrid.init;

import lombok.extern.slf4j.Slf4j;
import org.javaup.hybrid.config.HybridMilvusProperties;
import org.javaup.hybrid.service.HybridCollectionManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 初始化器
 * @author: 阿星不是程序员
 **/
/**
 * 应用启动时自动初始化 Hybrid Collection（受配置开关控制）。
 * <p>
 * 默认关闭（initialize-on-startup=false），避免每次启动都重建 Collection。
 * 如果需要自动初始化，在 application.yaml 中把 app.hybrid.milvus.initialize-on-startup 改成 true。
 */
@Slf4j
@Component
public class HybridSearchInitializer implements CommandLineRunner {

    private final HybridMilvusProperties properties;
    private final HybridCollectionManager collectionManager;

    public HybridSearchInitializer(HybridMilvusProperties properties,
                                   HybridCollectionManager collectionManager) {
        this.properties = properties;
        this.collectionManager = collectionManager;
    }

    @Override
    public void run(String... args) {
        if (!properties.isInitializeOnStartup()) {
            log.info("Hybrid Collection 自动初始化已关闭，跳过。如需开启请设置 app.hybrid.milvus.initialize-on-startup=true");
            return;
        }
        log.info("开始自动初始化 Hybrid Collection...");
        collectionManager.initializeIfNeeded();
        log.info("Hybrid Collection 自动初始化完成");
    }
}
