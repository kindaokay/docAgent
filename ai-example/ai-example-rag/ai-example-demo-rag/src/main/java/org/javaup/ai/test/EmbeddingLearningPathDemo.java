package org.javaup.ai.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 演示如何使用 Embedding 给“学习诉求”和“课程路线”做语义匹配
 * @author: 阿星不是程序员
 **/
/**
 * 演示如何使用 Embedding 给“学习诉求”和“课程路线”做语义匹配。
 * 这里直接调用 SiliconFlow 的 Embedding API，不依赖项目里的 Spring AI 配置。
 */
public class EmbeddingLearningPathDemo {

    public static void main(String[] args) throws Exception {
        String apiKey = "请替换成你的 SiliconFlow API Key";
        String apiUrl = "https://api.siliconflow.cn/v1/embeddings";
        String modelName = "Qwen/Qwen3-Embedding-8B";

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
        ObjectMapper objectMapper = new ObjectMapper();

        List<LearningPath> learningPaths = List.of(
            new LearningPath(
                "P001",
                "Java 高并发与分布式实战",
                "已经掌握 Spring Boot，希望补齐缓存、消息队列、限流熔断和分布式事务的同学",
                "Spring Boot、Redis、Kafka、分布式锁、幂等设计、秒杀系统",
                "能独立设计高并发下单链路，并定位常见性能瓶颈"
            ),
            new LearningPath(
                "P002",
                "MySQL 与 Redis 性能优化路线",
                "经常写 SQL，但对索引设计、慢查询治理和缓存一致性还不够熟的同学",
                "索引优化、执行计划、锁机制、缓存击穿、缓存雪崩、热点 Key",
                "能系统分析数据库慢查询问题，并设计稳定的缓存策略"
            ),
            new LearningPath(
                "P003",
                "Spring AI 与 RAG 项目实战",
                "想用 Java 做 AI 应用，希望掌握文档切块、向量化、检索增强和答案生成流程的同学",
                "Spring AI、Embedding、向量数据库、Chunk、Prompt、RAG、知识库问答",
                "能搭建一个完整的智能问答系统，并理解从文本到召回的核心链路"
            ),
            new LearningPath(
                "P004",
                "前端工程化与交互设计",
                "需要独立完成管理后台或业务门户，希望系统掌握组件化和工程化实践的同学",
                "Vue、React、TypeScript、状态管理、构建优化、组件设计",
                "能设计清晰可维护的前端工程结构，并完成复杂页面开发"
            ),
            new LearningPath(
                "P005",
                "云原生部署与 DevOps 路线",
                "负责服务上线和日常运维，希望掌握容器化、CI/CD 和服务可观测性的同学",
                "Docker、Kubernetes、GitHub Actions、灰度发布、监控告警、日志采集",
                "能把 Java 服务稳定部署到容器平台，并建立基础运维体系"
            ),
            new LearningPath(
                "P006",
                "数据分析与可视化入门",
                "想做报表分析、经营复盘和指标看板，希望提升数据建模与图表表达能力的同学",
                "数据清洗、指标体系、BI 报表、可视化图表、业务分析、经营复盘",
                "能围绕业务问题搭建基础分析模型，并输出清晰的数据结论"
            )
        );

        String learnerNeed = """
            我已经会一点 Spring Boot，最近想做一个能读取文档、切分文本块、
            计算相似度并回答问题的 Java AI 项目，最好还能顺手学会向量检索和 RAG 的完整链路。
            """;

        runSemanticMatch(httpClient, objectMapper, apiKey, apiUrl, modelName, learningPaths, learnerNeed, 3);
    }

    private static void runSemanticMatch(HttpClient httpClient,
                                         ObjectMapper objectMapper,
                                         String apiKey,
                                         String apiUrl,
                                         String modelName,
                                         List<LearningPath> learningPaths,
                                         String learnerNeed,
                                         int topK) throws Exception {
        List<String> pathPortraits = learningPaths.stream()
            .map(EmbeddingLearningPathDemo::buildPortrait)
            .toList();

        System.out.println("=== Embedding 学习路线匹配 Demo ===");
        System.out.println("Embedding 模型: " + modelName);
        System.out.println("候选路线数量: " + learningPaths.size());
        System.out.println();
        System.out.println("学习诉求:");
        System.out.println(learnerNeed);

        List<float[]> pathVectors = embedTexts(httpClient, objectMapper, apiKey, apiUrl, modelName, pathPortraits);
        float[] needVector = embedText(httpClient, objectMapper, apiKey, apiUrl, modelName, learnerNeed);

        System.out.println("向量生成完成，向量维度: " + pathVectors.get(0).length);
        System.out.println();
        System.out.println("--- Top 匹配结果 ---");

        List<MatchResult> results = new ArrayList<>();
        for (int i = 0; i < learningPaths.size(); i++) {
            LearningPath learningPath = learningPaths.get(i);
            double similarity = cosineSimilarity(needVector, pathVectors.get(i));
            results.add(new MatchResult(learningPath, similarity));
        }

        results.stream()
            .sorted(Comparator.comparingDouble(MatchResult::similarity).reversed())
            .limit(topK)
            .forEachOrdered(result -> printResult(result, buildPortrait(result.learningPath())));
    }

    private static float[] embedText(HttpClient httpClient,
                                     ObjectMapper objectMapper,
                                     String apiKey,
                                     String apiUrl,
                                     String modelName,
                                     String text) throws Exception {
        return embedTexts(httpClient, objectMapper, apiKey, apiUrl, modelName, List.of(text)).get(0);
    }

    private static List<float[]> embedTexts(HttpClient httpClient,
                                            ObjectMapper objectMapper,
                                            String apiKey,
                                            String apiUrl,
                                            String modelName,
                                            List<String> texts) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", texts);
        requestBody.put("encoding_format", "float");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Embedding API 调用失败，HTTP " + response.statusCode() + "，响应体: " + response.body());
        }

        return parseEmbeddings(objectMapper, response.body());
    }

    private static List<float[]> parseEmbeddings(ObjectMapper objectMapper, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("Embedding API 响应缺少 data 字段，响应体: " + responseBody);
        }

        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new IllegalStateException("Embedding API 响应缺少 embedding 字段，响应体: " + responseBody);
            }

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }
            vectors.add(vector);
        }

        return vectors;
    }

    private static String buildPortrait(LearningPath learningPath) {
        return """
            路线编号：%s
            路线名称：%s
            适合人群：%s
            核心关键词：%s
            学完之后：%s
            """.formatted(
            learningPath.code(),
            learningPath.title(),
            learningPath.audience(),
            learningPath.keywords(),
            learningPath.outcome()
        );
    }

    private static void printResult(MatchResult result, String portrait) {
        LearningPath path = result.learningPath();
        System.out.printf("[%s] %s -> 相似度 %.4f%n", path.code(), path.title(), result.similarity());
        System.out.println("画像内容:");
        System.out.println(portrait);
        System.out.println();
    }

    private static double cosineSimilarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("向量维度不一致，无法计算相似度");
        }

        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;

        for (int i = 0; i < left.length; i++) {
            dotProduct += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record LearningPath(String code, String title, String audience, String keywords, String outcome) {
    }

    private record MatchResult(LearningPath learningPath, double similarity) {
    }

}
