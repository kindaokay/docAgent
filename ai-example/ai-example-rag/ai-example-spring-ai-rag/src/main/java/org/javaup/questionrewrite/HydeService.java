package org.javaup.questionrewrite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * HyDE（Hypothetical Document Embeddings）服务
 * <p>
 * 核心思路：不改写问题本身，而是先让大模型"脑补"一个可能的答案，然后用这个假设答案去检索。
 * <p>
 * 为什么有效？
 * 假设答案的文本风格和知识库文档更接近。比如用户问"微服务之间怎么通信"，
 * 假设回答会包含"REST、gRPC、RabbitMQ、Kafka、解耦、削峰"等专业术语，
 * 这些词和知识库文档的用词高度重合，用假设回答的向量去检索，命中率比用原始短问题高很多。
 * <p>
 * 流程对比：
 * - 传统RAG：用户问题 → 检索 → 生成答案
 * - HyDE：  用户问题 → 生成假设答案 → 用假设答案检索 → 生成最终答案
 * <p>
 * 适用场景：用户提问很短、很模糊的场景。
 * 注意：如果用户的问题本身就很具体、很完整，HyDE反而可能引入噪音。另外会多一次LLM调用，延迟增加300-800ms。
 */
@Slf4j
@Service
public class HydeService {

    private final ChatClient chatClient;

    /**
     * HyDE Prompt
     * 要求大模型生成包含专业术语和概念的假设性回答，不需要完全准确
     */
    private static final String HYDE_PROMPT = """
        请根据以下问题，生成一段可能的回答。
        这段回答不需要完全准确，但应该包含相关的专业术语和概念。
        直接输出回答内容，不要加任何前缀或解释。

        问题：{question}
        """;

    public HydeService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 生成假设性回答
     * <p>
     * 实际项目中的使用方式：
     * 1. 调用此方法生成假设回答
     * 2. 用假设回答（而不是原始问题）去向量库做 similaritySearch
     * 3. 检索到的文档 + 原始问题一起喂给大模型生成最终答案
     *
     * @param question 用户原始问题
     * @return 假设性回答（包含领域术语，适合用于向量检索）
     */
    public String generateHypothetical(String question) {
        String hypothetical = chatClient.prompt()
                .user(u -> u.text(HYDE_PROMPT).param("question", question))
                .call()
                .content();

        log.info("HyDE假设回答: {}", hypothetical);
        return hypothetical;
    }
}
