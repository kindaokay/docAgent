package org.javaup.ai.controller;

import org.javaup.ai.service.DocumentPreprocessService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * Spring AI原生的TokenTextSplitter使用示例
 */
@RestController
@RequestMapping("/rag")
public class DocumentController {
    
    private DocumentPreprocessService documentPreprocessService;

    public DocumentController(DocumentPreprocessService documentPreprocessService){
        this.documentPreprocessService = documentPreprocessService;
    }

    @RequestMapping("/read")
    public List<Document> readDocument(@RequestParam("filePath") String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("文件不存在或不是有效文件: " + filePath);
        }
        return documentPreprocessService.process(file);
    }

   
}