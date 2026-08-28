package org.javaup.ai.controller;

import org.javaup.ai.service.DirectToolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
@RestController
@RequestMapping("/test")
public class TestController {
    
    private DirectToolService directToolService;

    public TestController(DirectToolService directToolService) {
        this.directToolService = directToolService;
    }

    @GetMapping("/chat")
    public String chat() {
        return directToolService.checkAttendance("0001","04:10");
    }

}
