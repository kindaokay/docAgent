package org.javaup.tika;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * Spring AI TikaDocumentReader 演示 —— 一键集成方式
 * <p>
 * 演示如何用Spring AI封装的TikaDocumentReader解析文件。
 * 对比原生Tika API（TikaDemoController），这种方式代码更简洁，
 * 返回的Document对象可以直接传给Spring AI的切块器和Embedding模型。
 * <p>
 * 测试方法：
 * curl "http://localhost:7092/rag/tika/spring-ai/parse?filePath=/tmp/产品手册.pdf"
 */
@Slf4j
@RestController
@RequestMapping("/rag/tika/spring-ai")
public class SpringAiTikaDemoController {

    /**
     * 用Spring AI的TikaDocumentReader解析文件
     * <p>
     * 一行代码搞定：自动检测格式 → 解析内容 → 封装成Document对象
     */
    @GetMapping("/parse")
    public Map<String, Object> parse(@RequestParam("filePath") String filePath) {
        File file = new File(filePath);
        log.info("Spring AI方式解析文件: {}", filePath);

        // 核心就这一行：TikaDocumentReader自动完成检测+解析+封装
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(file));
        List<Document> documents = reader.get();

        log.info("解析完成，得到 {} 个Document", documents.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", file.getName());
        result.put("documentCount", documents.size());
        result.put("documents", documents.stream().map(doc -> {
            Map<String, Object> docMap = new LinkedHashMap<>();
            docMap.put("charCount", doc.getText().length());
            docMap.put("content", doc.getText());
            docMap.put("metadata", doc.getMetadata());
            return docMap;
        }).collect(Collectors.toList()));
        return result;
    }
}
