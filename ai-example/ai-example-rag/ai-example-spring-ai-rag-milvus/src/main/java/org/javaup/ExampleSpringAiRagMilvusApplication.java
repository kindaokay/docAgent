package org.javaup;

import org.javaup.ai.config.MilvusDemoProperties;
import org.javaup.hybrid.config.HybridMilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 启动类
 * @author: 阿星不是程序员
 **/
/**
 * Spring AI + Milvus 向量检索示例启动类。
 */
@SpringBootApplication
@EnableConfigurationProperties({MilvusDemoProperties.class, HybridMilvusProperties.class})
public class ExampleSpringAiRagMilvusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleSpringAiRagMilvusApplication.class, args);
    }

}
