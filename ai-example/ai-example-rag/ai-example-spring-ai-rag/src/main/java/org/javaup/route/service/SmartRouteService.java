package org.javaup.route.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.route.model.RouteChatResponse;
import org.javaup.route.model.RouteIntent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
/**
 * 示例版主服务。
 * 为了便于理解，路由、示例数据、会话历史都收在这一个类里。
 */
@Slf4j
@Service
public class SmartRouteService {

    private static final String DEFAULT_SESSION_ID = "route-demo-session";
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)JU[-_]?\\d{8}[-_]?\\d{4}");

    private final RouteIntentClassifier routeIntentClassifier;
    private final ChatClient chatClient;

    private final Map<String, List<String>> sessionStore = new ConcurrentHashMap<>();

    private static final List<KnowledgeItem> KNOWLEDGE_ITEMS = List.of(
        new KnowledgeItem("发票规则", "训练营支持电子普通发票，支付成功 30 天内都可以申请，入口在“我的订单 -> 发票申请”。", List.of("发票", "开票", "税号")),
        new KnowledgeItem("直播回放", "直播课结束后 2 小时内会生成回放，开营期间可以反复观看。", List.of("回放", "直播", "补课")),
        new KnowledgeItem("结课证书", "结课证书需要课程进度达到 85%，作业完成率不低于 80%，并通过结营测评。", List.of("证书", "结课", "作业")),
        new KnowledgeItem("退款规则", "购买后 48 小时内，且未解锁超过 20% 内容时，可以申请退款。", List.of("退款", "退费", "售后"))
    );

    private static final Map<String, String> ORDER_DATA = Map.of(
        "JU-20260318-1001", "订单 JU-20260318-1001 已支付，课程权限已经开通。",
        "JU-20260318-1002", "订单 JU-20260318-1002 还在待支付，暂时没有开通学习权限。"
    );

    private static final Map<String, String> PROGRESS_DATA = Map.of(
        "DEMO-USER", "示例学员当前进度 72%，最近学到“检索路由策略”，还有 1 份作业待完成。"
    );

    private static final Map<String, String> SCHEDULE_DATA = Map.of(
        "CAMP-RAG-03", "示例班级下一次直播时间是 2026-03-20 20:00，主题是“多路由检索与答案兜底策略”。"
    );

    public SmartRouteService(RouteIntentClassifier routeIntentClassifier, ChatClient.Builder builder) {
        this.routeIntentClassifier = routeIntentClassifier;
        this.chatClient = builder.build();
    }

    /**
     * 一次完整对话主流程：
     * 1. 拿历史
     * 2. 判意图
     * 3. 按意图调用对应方法
     * 4. 记录本轮问答
     */
    public RouteChatResponse chat(String sessionId, String question) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        List<String> history = new ArrayList<>(sessionStore.getOrDefault(normalizedSessionId, List.of()));
        String historyText = formatHistory(history);

        RouteIntent intent = routeIntentClassifier.classify(question, historyText);
        String answer = switch (intent) {
            case KNOWLEDGE -> answerKnowledge(question, historyText);
            case TOOL -> answerTool(question);
            case CHITCHAT -> answerChitchat(question, historyText);
            case CLARIFY -> answerClarify(question);
        };

        appendHistory(normalizedSessionId, "用户：" + question);
        appendHistory(normalizedSessionId, "助手：" + answer);

        log.info("RouteChat | sessionId={} | question={} | intent={}",
            normalizedSessionId, question, intent.getCode());

        return new RouteChatResponse(
            normalizedSessionId,
            question,
            intent,
            intent.getLabel(),
            answer
        );
    }

    public void reset(String sessionId) {
        sessionStore.remove(normalizeSessionId(sessionId));
    }

    /**
     * 知识通道保留最核心的两步：
     * 1. 必要时补全问题
     * 2. 命中资料后组织成回答
     */
    private String answerKnowledge(String question, String historyText) {
        String standaloneQuestion = rewriteQuestionIfNeeded(question, historyText);
        List<KnowledgeItem> matchedItems = searchKnowledge(standaloneQuestion);
        if (matchedItems.isEmpty()) {
            return "这个问题更适合走知识检索，不过当前示例资料里没有命中内容。你可以试试问发票、回放、证书或者退款。";
        }

        String references = matchedItems.stream()
            .map(item -> item.title + "：" + item.content)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        try {
            return chatClient.prompt()
                .system("你是知识库助手，只根据给出的资料回答，不要编造。")
                .user(user -> user.text("""
                    用户问题：{question}

                    参考资料：
                    {references}
                    """)
                    .param("question", question)
                    .param("references", references))
                .call()
                .content();
        }
        catch (Exception exception) {
            return references;
        }
    }

    /**
     * 工具通道不做复杂封装，直接按关键词命中示例数据。
     */
    private String answerTool(String question) {
        String normalized = normalize(question);

        String orderId = extractOrderId(question);
        if (StringUtils.hasText(orderId)) {
            return ORDER_DATA.getOrDefault(orderId, "没有查到这个订单号，你可以先检查下输入是否正确。");
        }

        if (normalized.contains("进度") || normalized.contains("学到哪")) {
            return PROGRESS_DATA.get("DEMO-USER");
        }

        if (normalized.contains("直播") || normalized.contains("排期") || normalized.contains("上课")) {
            return SCHEDULE_DATA.get("CAMP-RAG-03");
        }

        return "这个问题应该走工具通道，不过示例里需要更具体一点，比如给订单号，或者直接问学习进度、直播排期。";
    }

    /**
     * 闲聊尽量简单，常见寒暄直接固定回复。
     */
    private String answerChitchat(String question, String historyText) {
        String normalized = normalize(question);
        if (normalized.contains("你好") || normalized.contains("您好") || normalized.contains("hello") || normalized.contains("hi")) {
            return "你好呀，我这边可以帮你判断该查知识库、查工具，还是先把问题问清楚。";
        }
        if (normalized.contains("谢谢") || normalized.contains("感谢")) {
            return "不客气，你后面继续问就行。";
        }
        try {
            return chatClient.prompt()
                .system("你是 JavaUp 的学习助手，闲聊时自然一点，不要突然展开长篇技术回答。")
                .user(user -> user.text("""
                    历史对话：
                    {history}

                    用户消息：
                    {question}
                    """)
                    .param("history", historyText)
                    .param("question", question))
                .call()
                .content();
        }
        catch (Exception exception) {
            return "收到，我们继续聊。";
        }
    }

    /**
     * 澄清通道不追求复杂生成，给用户一个容易接的话头就够了。
     */
    private String answerClarify(String question) {
        if (question.contains("推荐")) {
            return "可以呀，你是想让我推荐课程方向、具体训练营，还是学习路线？你随便补一句，我就能继续往下接。";
        }
        return "你这个问题我还差一点信息。你可以补充下你是想问课程规则、订单状态，还是学习进度？";
    }

    private String rewriteQuestionIfNeeded(String question, String historyText) {
        if (!StringUtils.hasText(historyText)
            || !StringUtils.hasText(question)
            || (question.length() > 10 && !question.contains("它") && !question.contains("这个") && !question.contains("那个"))) {
            return question;
        }
        try {
            return chatClient.prompt()
                .user(user -> user.text("""
                    请把用户当前问题改写成一个能独立理解的查询句。
                    只输出改写结果，不要解释。

                    历史对话：
                    {history}

                    当前问题：
                    {question}
                    """)
                    .param("history", historyText)
                    .param("question", question))
                .call()
                .content()
                .strip();
        }
        catch (Exception exception) {
            return question;
        }
    }

    private List<KnowledgeItem> searchKnowledge(String question) {
        String normalized = normalize(question);
        List<KnowledgeItem> result = new ArrayList<>();
        for (KnowledgeItem item : KNOWLEDGE_ITEMS) {
            for (String keyword : item.keywords) {
                if (normalized.contains(keyword)) {
                    result.add(item);
                    break;
                }
            }
        }
        return result;
    }

    private String extractOrderId(String question) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(question);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group().toUpperCase(Locale.ROOT).replace('_', '-');
    }

    private void appendHistory(String sessionId, String line) {
        sessionStore.computeIfAbsent(sessionId, key -> Collections.synchronizedList(new ArrayList<>())).add(line);
    }

    private String normalizeSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId.trim() : DEFAULT_SESSION_ID;
    }

    private String formatHistory(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "无历史对话";
        }
        return String.join("\n", history);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static class KnowledgeItem {

        private final String title;
        private final String content;
        private final List<String> keywords;

        private KnowledgeItem(String title, String content, List<String> keywords) {
            this.title = title;
            this.content = content;
            this.keywords = keywords;
        }
    }
}
