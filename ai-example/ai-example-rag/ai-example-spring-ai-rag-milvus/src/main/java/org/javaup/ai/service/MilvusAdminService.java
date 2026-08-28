package org.javaup.ai.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.grpc.GetLoadStateResponse;
import io.milvus.grpc.IndexDescription;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.R;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.collection.GetLoadStateParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.index.DescribeIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.MilvusCollectionStatusResponse;
import org.javaup.ai.model.MilvusIndexInfo;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class MilvusAdminService {

    private final MilvusServiceClient milvusClient;
    private final MilvusVectorStore milvusVectorStore;
    private final MilvusVectorStoreProperties vectorStoreProperties;
    private final MilvusServiceClientProperties clientProperties;

    public MilvusAdminService(MilvusServiceClient milvusClient,
                              MilvusVectorStore milvusVectorStore,
                              MilvusVectorStoreProperties vectorStoreProperties,
                              MilvusServiceClientProperties clientProperties) {
        this.milvusClient = milvusClient;
        this.milvusVectorStore = milvusVectorStore;
        this.vectorStoreProperties = vectorStoreProperties;
        this.clientProperties = clientProperties;
    }

    /**
     * 清理旧 Collection 后，重新触发 Spring AI 的 Milvus 初始化流程。
     */
    public void recreateCollection() {
        if (collectionExists()) {
            // 先 release 再 drop，是 Milvus 删除已加载 Collection 时更稳妥的顺序。
            milvusClient.releaseCollection(ReleaseCollectionParam.newBuilder()
                    .withDatabaseName(vectorStoreProperties.getDatabaseName())
                    .withCollectionName(vectorStoreProperties.getCollectionName())
                    .build());

            R<?> dropResult = milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withDatabaseName(vectorStoreProperties.getDatabaseName())
                    .withCollectionName(vectorStoreProperties.getCollectionName())
                    .build());
            if (dropResult.getException() != null) {
                throw new IllegalStateException("删除 Milvus Collection 失败", dropResult.getException());
            }
            log.info("已删除 Collection，collection={}", vectorStoreProperties.getCollectionName());
        }

        try {
            // 按当前 application.yaml 中的配置重新初始化 Collection。
            // 这里会完成 Schema 初始化、索引创建，并把 Collection 加载到内存。
            milvusVectorStore.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("重新初始化 Milvus Collection 失败", e);
        }
        log.info("Milvus Collection 已重建并加载到内存，collection={}", vectorStoreProperties.getCollectionName());
    }

    /**
     * 汇总 Collection 是否存在、是否已 load、统计信息和索引状态，便于排查 demo 环境问题。
     */
    public MilvusCollectionStatusResponse getCollectionStatus() {
        boolean exists = collectionExists();
        if (!exists) {
            return new MilvusCollectionStatusResponse(
                    resolveUri(),
                    vectorStoreProperties.getDatabaseName(),
                    vectorStoreProperties.getCollectionName(),
                    false,
                    "NOT_FOUND",
                    Map.of(),
                    List.of(),
                    "Collection 不存在"
            );
        }

        GetLoadStateResponse loadStateResponse = requireData(milvusClient.getLoadState(GetLoadStateParam.newBuilder()
                .withDatabaseName(vectorStoreProperties.getDatabaseName())
                .withCollectionName(vectorStoreProperties.getCollectionName())
                .build()), "获取 Collection loadState 失败");

        GetCollectionStatisticsResponse statisticsResponse =
                requireData(milvusClient.getCollectionStatistics(GetCollectionStatisticsParam.newBuilder()
                        .withDatabaseName(vectorStoreProperties.getDatabaseName())
                        .withCollectionName(vectorStoreProperties.getCollectionName())
                        .build()), "获取 Collection 统计信息失败");

        DescribeIndexResponse describeIndexResponse = requireData(milvusClient.describeIndex(DescribeIndexParam.newBuilder()
                .withDatabaseName(vectorStoreProperties.getDatabaseName())
                .withCollectionName(vectorStoreProperties.getCollectionName())
                .build()), "获取索引信息失败");

        DescribeCollectionResponse describeCollectionResponse =
                requireData(milvusClient.describeCollection(DescribeCollectionParam.newBuilder()
                        .withDatabaseName(vectorStoreProperties.getDatabaseName())
                        .withCollectionName(vectorStoreProperties.getCollectionName())
                        .build()), "获取 Collection 描述失败");

        return new MilvusCollectionStatusResponse(
                resolveUri(),
                vectorStoreProperties.getDatabaseName(),
                vectorStoreProperties.getCollectionName(),
                true,
                loadStateResponse.getState().name(),
                toStatisticsMap(statisticsResponse.getStatsList()),
                describeIndexResponse.getIndexDescriptionsList().stream().map(this::toIndexInfo).toList(),
                describeCollectionResponse.toString()
        );
    }

    private boolean collectionExists() {
        R<Boolean> result = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withDatabaseName(vectorStoreProperties.getDatabaseName())
                .withCollectionName(vectorStoreProperties.getCollectionName())
                .build());
        if (result.getException() != null) {
            throw new IllegalStateException("检查 Collection 是否存在失败", result.getException());
        }
        return Boolean.TRUE.equals(result.getData());
    }

    private <T> T requireData(R<T> result, String errorMessage) {
        // 把底层返回值统一转成异常风格，控制层就不用重复判空和判断异常了。
        if (result.getException() != null) {
            throw new IllegalStateException(errorMessage, result.getException());
        }
        return result.getData();
    }

    private Map<String, String> toStatisticsMap(List<KeyValuePair> pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (KeyValuePair pair : pairs) {
            result.put(pair.getKey(), pair.getValue());
        }
        return result;
    }

    private MilvusIndexInfo toIndexInfo(IndexDescription indexDescription) {
        Map<String, String> params = new LinkedHashMap<>();
        for (KeyValuePair pair : indexDescription.getParamsList()) {
            params.put(pair.getKey(), pair.getValue());
        }
        return new MilvusIndexInfo(
                indexDescription.getFieldName(),
                indexDescription.getIndexName(),
                indexDescription.getState().name(),
                indexDescription.getIndexedRows(),
                indexDescription.getTotalRows(),
                params
        );
    }

    private String resolveUri() {
        if (StringUtils.hasText(clientProperties.getUri())) {
            return clientProperties.getUri();
        }
        return clientProperties.getHost() + ":" + clientProperties.getPort();
    }
}
