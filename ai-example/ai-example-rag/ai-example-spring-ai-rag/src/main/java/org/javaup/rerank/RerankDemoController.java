package org.javaup.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * 重排序演示 Controller —— 自包含的Reranker效果对比Demo
 * <p>
 * 设计思路：
 * 这个Controller不依赖向量库、不依赖其他包的任何Bean，
 * 内置一组模拟的候选文档（假设是混合检索召回的结果），
 * 直接调用SiliconFlowRerankPostProcessor做重排序，
 * 返回重排序前后的对比结果，一目了然地展示Reranker的效果。
 * <p>
 * 配置好SiliconFlow的apiKey后，启动项目直接访问即可测试。
 * <p>
 * 测试地址（端口默认7092）：
 * <ul>
 *   <li>默认问题：GET /rag/rerank/demo</li>
 *   <li>自定义问题：GET /rag/rerank/demo?question=Spring事务失效的常见原因</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/rag/rerank")
public class RerankDemoController {

    /** 注入Reranker后处理器，它实现了Spring AI的DocumentPostProcessor接口 */
    private final SiliconFlowRerankPostProcessor reranker;

    public RerankDemoController(SiliconFlowRerankPostProcessor reranker) {
        this.reranker = reranker;
    }

    /**
     * 模拟的候选文档 —— 假设这些是混合检索（向量+BM25+RRF融合）召回的结果
     * <p>
     * 场景设计：用户问"Spring事务失效的常见原因"
     * - 文档1：讲Bean生命周期，和事务无关，属于"完全不搭"的噪音
     * - 文档2：讲自调用导致事务失效，高度相关
     * - 文档3：讲Spring AOP代理机制，沾点边但不直接回答问题
     * - 文档4：讲private方法和异常吞掉导致事务失效，高度相关
     * - 文档5：讲自动配置原理，和事务无关，又一个噪音
     * - 文档6：讲传播行为和引擎不支持事务，高度相关
     * <p>
     * 混合检索只是按关键词命中和向量距离随机排的，顺序不代表相关度。
     * Reranker的作用就是把文档2、4、6推到最前面。
     */
    private static final List<Document> MOCK_CANDIDATES = List.of(
            // 噪音文档：和"事务失效"毫无关系，只是同属Spring知识体系被召回了
            new Document("Spring Bean的生命周期包括实例化、属性注入、初始化、销毁四个阶段，"
                    + "其中初始化阶段会调用@PostConstruct标注的方法和InitializingBean接口的afterPropertiesSet方法。"),

            // 高相关：自调用导致事务失效，这是最经典的事务失效场景之一
            new Document("@Transactional注解的方法如果被同类中的其他方法直接调用（即自调用），"
                    + "由于绕过了AOP代理，事务不会生效。解决方案是通过注入自身Bean或使用AopContext.currentProxy()。"),

            // 沾点边：AOP代理机制和事务有关联，但并不直接回答"事务失效原因"
            new Document("Spring AOP默认使用JDK动态代理（针对接口）或CGLIB代理（针对类），"
                    + "代理对象会拦截方法调用并在前后插入横切逻辑，如事务管理、日志记录等。"),

            // 高相关：访问修饰符和异常吞掉导致事务失效
            new Document("当@Transactional标注在private、protected或default方法上时，"
                    + "CGLIB代理无法拦截这些非public方法，导致事务注解不生效。"
                    + "此外，如果方法内部catch了异常没有重新抛出，事务也不会回滚。"),

            // 噪音文档：自动配置和事务失效没关系
            new Document("Spring Boot自动配置的核心原理是通过@EnableAutoConfiguration注解，"
                    + "加载META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports中声明的配置类。"),

            // 高相关：传播行为设置和数据库引擎导致事务失效
            new Document("@Transactional的propagation属性设为NOT_SUPPORTED或NEVER时，"
                    + "方法将不在事务中执行。另外，数据库引擎不支持事务（如MyISAM）也会导致事务失效。")
    );

    /**
     * 重排序演示接口 —— 展示同一批文档重排序前后的顺序变化
     * <p>
     * 返回的JSON包含三个部分：
     * - before_rerank：重排序前的文档顺序（混合检索返回的原始顺序）
     * - after_rerank：重排序后的文档顺序（Reranker按相关性打分后的顺序），每个文档附带score
     * - latencyMs：整个重排序耗时（毫秒），通常在200-500ms
     * <p>
     * 测试方法：
     * 1. 默认问题：GET /rag/rerank/demo
     * 2. 自定义问题：GET /rag/rerank/demo?question=你想问的问题
     * <p>
     * 预期效果：
     * 重排序前，6个文档是随机顺序；
     * 重排序后，3个和"事务失效"高度相关的文档应该排在最前面，
     * "Bean生命周期"和"自动配置"这两个噪音文档应该排到最后或被截掉。
     */
    @GetMapping("/demo")
    public Map<String, Object> demo(
            @RequestParam(value = "question",
                    defaultValue = "Spring事务失效的常见原因") String question) {

        long start = System.currentTimeMillis();

        // ========== 第一步：记录重排序前的文档顺序（作为对比基准） ==========
        List<Map<String, String>> beforeList = new ArrayList<>();
        for (int i = 0; i < MOCK_CANDIDATES.size(); i++) {
            Map<String, String> item = new HashMap<>();
            item.put("rank", String.valueOf(i + 1));
            item.put("text", MOCK_CANDIDATES.get(i).getText());
            beforeList.add(item);
        }

        // ========== 第二步：调用Reranker做重排序 ==========
        // Query是Spring AI的查询封装，这里只用最简单的单参数构造
        // reranker.process()内部会调SiliconFlow API，返回按相关性排好序的文档
        List<Document> reranked = reranker.process(new Query(question), MOCK_CANDIDATES);

        // ========== 第三步：记录重排序后的文档顺序（附带Reranker打的分数） ==========
        List<Map<String, Object>> afterList = new ArrayList<>();
        for (int i = 0; i < reranked.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            // score是Reranker打的相关性分数（0~1），越高越相关
            item.put("score", reranked.get(i).getMetadata().get("rerank_score"));
            item.put("text", reranked.get(i).getText());
            afterList.add(item);
        }

        long latency = System.currentTimeMillis() - start;

        // ========== 第四步：组装返回结果，方便前后对比 ==========
        Map<String, Object> result = new HashMap<>();
        result.put("question", question);
        result.put("candidateCount", MOCK_CANDIDATES.size());
        result.put("before_rerank", beforeList);
        result.put("after_rerank", afterList);
        result.put("latencyMs", latency);
        return result;
    }
}
