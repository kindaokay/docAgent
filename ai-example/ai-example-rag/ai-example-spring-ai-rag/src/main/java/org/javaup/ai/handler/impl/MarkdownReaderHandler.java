package org.javaup.ai.handler.impl;

import org.javaup.ai.handler.ReaderHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 处理器
 * @author: 阿星不是程序员
 **/
@Component
public class MarkdownReaderHandler implements ReaderHandler {

    @Override
    public boolean canHandle(File file) {
        return file.getName().toLowerCase().endsWith(".md");
    }

    @Override
    public List<Document> readhandle(File file) throws IOException {
        MarkdownDocumentReaderConfig config = 
                MarkdownDocumentReaderConfig.builder()
                // 遇到分割线创建新Document
                .withHorizontalRuleCreateDocument(true)
                // 包含代码块        
                .withIncludeCodeBlock(true)
                // 包含引用块        
                .withIncludeBlockquote(true)  
                // 添加文件名元数据
                .withAdditionalMetadata("filename", file.getName())
                .build();
        
        Resource resource = new FileSystemResource(file);
        return new MarkdownDocumentReader(resource, config).get();
    }
}