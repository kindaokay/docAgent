package org.javaup.tika;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * Apache Tika 原生API演示 —— 文档解析示例
 * <p>
 * 演示如何用原生Tika API解析文件，提取文本内容和元数据。
 * 这个Controller是独立的，不依赖项目中其他包的任何Bean。
 * <p>
 * 测试方法（端口默认7092）：
 * <ul>
 *   <li>解析文档：GET /rag/tika/parse?filePath=/tmp/xxx.pdf</li>
 *   <li>检测类型：GET /rag/tika/detect?filePath=/tmp/xxx.pdf</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/rag/tika")
public class TikaDemoController {

    private final Tika tika = new Tika();
    private final Parser parser = new AutoDetectParser();

    /**
     * 传入文件路径，用原生Tika解析出文本内容和元数据
     * <p>
     * curl "http://localhost:7092/rag/tika/parse?filePath=/tmp/产品手册.pdf"
     */
    @GetMapping("/parse")
    public Map<String, Object> parse(@RequestParam("filePath") String filePath) throws Exception {
        File file = new File(filePath);
        String filename = file.getName();
        log.info("开始解析文件: {}, 大小: {} KB", filePath, file.length() / 1024);

        // 1. 检测真实MIME类型
        String mimeType;
        try (InputStream stream = new FileInputStream(file)) {
            mimeType = tika.detect(stream, filename);
        }
        log.info("检测到MIME类型: {}", mimeType);

        // 2. 解析文本和元数据
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

        try (InputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        String text = handler.toString().trim();
        log.info("解析完成，文本长度: {} 字符", text.length());

        // 3. 收集元数据
        Map<String, String> metaMap = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            String value = metadata.get(name);
            if (value != null && !value.isBlank()) {
                metaMap.put(name, value);
            }
        }

        // 4. 组装返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", filename);
        result.put("mimeType", mimeType);
        result.put("charCount", text.length());
        result.put("content", text);
        result.put("metadata", metaMap);
        return result;
    }

    /**
     * 只检测文件的真实MIME类型，不做完整解析（轻量操作）
     * <p>
     * curl "http://localhost:7092/rag/tika/detect?filePath=/tmp/可疑文件.txt"
     */
    @GetMapping("/detect")
    public Map<String, Object> detect(@RequestParam("filePath") String filePath) throws Exception {
        File file = new File(filePath);
        String mimeType;
        try (InputStream stream = new FileInputStream(file)) {
            mimeType = tika.detect(stream, file.getName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", file.getName());
        result.put("mimeType", mimeType);
        result.put("sizeKB", file.length() / 1024);
        return result;
    }
}
