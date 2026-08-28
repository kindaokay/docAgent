package org.javaup.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String INTERVIEWER_SYSTEM_PROMPT = """
        你现在是一名严肃但专业的 Java 技术面试官。
        请围绕用户问题给出准确回答，优先解释底层原理，并在必要时补充简短示例。
        """;

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam(value = "question", defaultValue = "请介绍一下 Spring AI 的核心能力") String question) {
        return this.chatClient.prompt()
            .user(question)
            .call()
            .content();
    }

    @GetMapping(value = "/stream", produces = "text/html;charset=utf-8")
    public Flux<String> stream(
        @RequestParam(value = "question", defaultValue = "请用三点介绍 Spring AI 为什么适合 Java 项目") String question) {
        return this.chatClient.prompt()
            .user(question)
            .stream()
            .content();
    }

    @GetMapping("/interview")
    public String interview(
        @RequestParam(value = "question", defaultValue = "请介绍一下 HashMap 的实现原理") String question) {
        return this.chatClient.prompt()
            .system(INTERVIEWER_SYSTEM_PROMPT)
            .user(question)
            .call()
            .content();
    }

    @GetMapping("/ask-with-system")
    public String askWithSystem(
        @RequestParam(value = "system", required = false) String system,
        @RequestParam(value = "question", defaultValue = "请介绍一下 Spring AI") String question) {
        ChatClient.ChatClientRequestSpec requestSpec = this.chatClient.prompt();
        if (StringUtils.hasText(system)) {
            requestSpec = requestSpec.system(system);
        }
        return requestSpec.user(question)
            .call()
            .content();
    }

}
