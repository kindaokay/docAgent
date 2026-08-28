package org.javaup.hybrid.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 配置类
 * @author: 阿星不是程序员
 **/
/**
 * 注册 Milvus SDK V2 客户端。
 * <p>
 * Spring AI 的 Milvus 自动配置只注册了 V1 的 MilvusServiceClient，
 * 但混合检索需要用到 V2 的 HybridSearchReq / AnnSearchReq 等 API，
 * 所以这里额外注册一个 MilvusClientV2 Bean，复用 Spring AI 配置中的连接地址。
 */
@Configuration
public class MilvusClientV2Config {

    @Bean
    public MilvusClientV2 milvusClientV2(MilvusServiceClientProperties clientProperties) {
        // 从 Spring AI 的配置中拿到 Milvus 连接地址，避免重复配置
        String uri = clientProperties.getUri();
        if (uri == null || uri.isBlank()) {
            uri = "http://" + clientProperties.getHost() + ":" + clientProperties.getPort();
        }

        return new MilvusClientV2(ConnectConfig.builder()
                .uri(uri)
                .build());
    }
}
