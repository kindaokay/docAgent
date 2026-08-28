package org.javaup.ai.handler.impl;

import org.javaup.ai.handler.ReaderHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
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
public class HtmlReaderHandler implements ReaderHandler {

    @Override
    public boolean canHandle(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    @Override
    public List<Document> readhandle(File file) throws IOException {
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                // 只提取正文区域
                .selector("article, .content, main")  
                .charset("UTF-8")
                // 不包含链接
                .includeLinkUrls(false)
                // 元数据
                .metadataTags(List.of("author", "date"))
                // 自定义元数据
                .additionalMetadata("filename", file.getName())
                .build();
        return new JsoupDocumentReader(new FileSystemResource(file), config).get();
    }
}