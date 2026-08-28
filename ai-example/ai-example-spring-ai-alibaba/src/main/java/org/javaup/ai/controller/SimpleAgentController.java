package org.javaup.ai.controller;

import org.javaup.ai.service.SpringAiAlibabaAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/agent")
public class SimpleAgentController {

    private final SpringAiAlibabaAgentService agentService;

    public SimpleAgentController(SpringAiAlibabaAgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/hello")
    public String hello(
        @RequestParam(value = "question", defaultValue = "你好，请用三点介绍一下 Spring AI Alibaba ReactAgent 的作用")
        String question) {
        return this.agentService.simpleReply(question);
    }

}
