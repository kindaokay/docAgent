package org.javaup.questionrewrite;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 自定义查询改写服务
 * <p>
 * 核心能力：指代消解 + 上下文补全 + 口语转书面
 * <p>
 * 为什么需要改写？
 * - 用户说"那它有没有证书"，检索系统不知道"它"是什么
 * - 用户说"哪个更适合微服务"，省略了比较对象
 * - 用户说"服务挂了咋整"，知识库里写的是"服务异常排查与恢复流程"
 * <p>
 * 改写就是在用户提问和向量检索之间加一个"翻译层"，把人话翻译成检索能听懂的话。
 * <p>
 * 设计要点：
 * 1. 启发式前置判断（needsRewrite）：先用简单规则判断是否需要改写，能省掉30-40%的无效LLM调用
 * 2. 结果缓存（rewriteWithCache）：同一session内相同问题+相同历史，改写结果直接复用
 * 3. 安全兜底（safeRewrite）：LLM调用失败时回退到原始问题，不影响主流程
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final ChatClient chatClient;

    /**
     * 改写Prompt —— 把前三种策略（指代消解、上下文补全、口语转书面）合在一个Prompt里
     * <p>
     * 第4条规则很关键：防止大模型过度改写，把本来就很好的问题改得面目全非
     */
    private static final String REWRITE_PROMPT = """
        你是一个查询改写助手。将用户的当前提问改写为独立、完整、适合检索的查询语句。

        改写规则：
        1. 将代词替换为具体实体
        2. 补全省略的信息
        3. 口语化表达转为书面表达
        4. 如果提问已经完整清晰，原样输出
        5. 只输出改写后的查询，不要解释

        对话历史：
        {chat_history}

        当前提问：{question}
        """;

    /**
     * 改写结果缓存
     * key = sessionId + ":" + question.hashCode()
     * value = 改写后的查询
     * <p>
     * 同一session内，相同的问题+相同的历史，改写结果可以缓存，避免重复调用LLM
     */
    private final Map<String, String> rewriteCache = new ConcurrentHashMap<>();

    public QueryRewriteService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 带缓存的改写入口
     * <p>
     * 适合在Controller中直接调用，同一session内相同问题不会重复调用LLM
     *
     * @param sessionId 会话ID，用于构建缓存key
     * @param question  用户原始问题
     * @param history   对话历史
     * @return 改写后的查询（如果缓存命中则直接返回缓存结果）
     */
    public String rewriteWithCache(String sessionId, String question, List<Message> history) {
        String cacheKey = sessionId + ":" + question.hashCode();
        return rewriteCache.computeIfAbsent(cacheKey, k -> safeRewrite(question, history));
    }

    /**
     * 核心改写方法：指代消解 + 上下文补全 + 口语转书面
     * <p>
     * 流程：
     * 1. 先用启发式规则判断是否需要改写（省掉不必要的LLM调用）
     * 2. 格式化对话历史为文本
     * 3. 调用LLM进行改写
     * 4. 校验改写结果（防止异常输出）
     *
     * @param question 用户原始问题
     * @param history  对话历史（用于理解指代和省略）
     * @return 改写后的查询
     */
    public String rewrite(String question, List<Message> history) {
        // 先判断是否需要改写，节省不必要的LLM调用
        if (!needsRewrite(question, history)) {
            return question;
        }

        String historyText = formatHistory(history);
        String result = chatClient.prompt()
                .user(u -> u.text(REWRITE_PROMPT)
                        .param("chat_history", historyText)
                        .param("question", question))
                .call()
                .content();

        return validateResult(result, question);
    }

    /**
     * 带兜底的安全改写
     * <p>
     * LLM调用可能超时、返回空、返回格式异常。
     * 改写失败时，回退到原始问题，而不是报错 —— 这是生产环境的必备策略。
     *
     * @param question 用户原始问题
     * @param history  对话历史
     * @return 改写后的查询，失败时返回原始问题
     */
    public String safeRewrite(String question, List<Message> history) {
        try {
            String result = rewrite(question, history);
            // 基本校验：非空、长度合理、不是原样返回（说明确实做了改写）
            if (result != null && !result.isBlank()
                    && result.length() < 500
                    && !result.equals(question)) {
                return result;
            }
            return question;
        } catch (Exception e) {
            log.warn("问题改写失败，回退到原始问题: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 启发式前置判断：是否需要改写
     * <p>
     * 用简单规则快速过滤，能省掉30-40%的无效LLM调用：
     * - 没有对话历史 + 问题够长 → 大概率不需要改写
     * - 包含代词（它、这个、那个等）→ 大概率需要指代消解
     * - 问题很短（<10字）→ 可能省略了信息，需要补全
     */
    private boolean needsRewrite(String question, List<Message> history) {
        if (history == null || history.isEmpty()) {
            // 没有对话历史，只需要判断是否口语化（可选）
            // 太短的问题可能需要补全
            return question.length() < 6;
        }
        // 包含代词，大概率需要改写
        String[] pronouns = {"它", "这个", "那个", "他", "她", "上面", "刚才", "之前"};
        for (String p : pronouns) {
            if (question.contains(p)) {
                return true;
            }
        }
        // 问题很短，可能省略了信息
        return question.length() < 10;
    }

    /**
     * 校验改写结果
     * 防止LLM返回空、超长、或格式异常的内容
     */
    private String validateResult(String result, String original) {
        if (result == null || result.isBlank() || result.length() > 500) {
            // 改写失败或结果异常，回退到原始问题
            return original;
        }
        return result.strip();
    }

    /**
     * 格式化对话历史为文本，喂给改写Prompt
     * 格式：用户：xxx\n助手：xxx\n
     */
    private String formatHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : history) {
            String role = msg instanceof UserMessage ? "用户" : "助手";
            sb.append(role).append("：").append(msg.getText()).append("\n");
        }
        return sb.toString();
    }
}
