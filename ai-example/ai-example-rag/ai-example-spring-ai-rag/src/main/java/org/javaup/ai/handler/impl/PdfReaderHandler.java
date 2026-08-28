package org.javaup.ai.handler.impl;

import org.javaup.ai.handler.ReaderHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
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
public class PdfReaderHandler implements ReaderHandler {

    @Override
    public boolean canHandle(File file) {
        return file.getName().toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> readhandle(File file) throws IOException {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                // 忽略顶部50单位（跳过页眉）
                .withPageTopMargin(50)
                // 忽略底部50单位（跳过页脚）
                .withPageBottomMargin(50)
                // 每页一个Document
                .withPagesPerDocument(1)
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                        // 每页额外忽略前0行
                        .withNumberOfTopTextLinesToDelete(0) 
                        .build())
                .build();

        Resource resource = new FileSystemResource(file);
        return new PagePdfDocumentReader(resource, config).get();
    }
}