package org.javaup.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.handler.ReaderHandlerContext;
import org.javaup.ai.split.SpringAiAlibabaRecursiveTextSplit;
import org.javaup.ai.util.DocumentClearHandler;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Slf4j
@Service
public class DocumentPreprocessService {

    private final ReaderHandlerContext readerHandlerContext;

    public DocumentPreprocessService(ReaderHandlerContext readerHandlerContext) {
        this.readerHandlerContext = readerHandlerContext;
    }

    /**
     * 处理单个文件
     */
    public List<Document> process(File file) {
        try {
            // 1. 读取文档
            log.info("开始读取文档: {}", file.getName());
            List<Document> docs = readerHandlerContext.read(file);
            log.info("读取完成，共 {} 个Document", docs.size());

            // 2. 清洗文档
            log.info("开始清洗文档");
            // 注意：不同分片器要配不同的清洗策略。
            // 1) TokenTextSplitter / OverlapParagraphTextSplit 偏固定大小切块，可以把空白压成一行；
            //docs = DocumentClearHandler.clearDocumentsForFlatSplit(docs);
            
            
            // 2) Spring AI Alibaba 的递归分片依赖 \n\n、\n、句号等边界，不能把这些结构信息清掉。
            docs = DocumentClearHandler.clearDocumentsForRecursiveSplit(docs);
            log.info("清洗完成");

            // 3. 添加元数据
            log.info("添加元数据");
            for (Document doc : docs) {
                doc.getMetadata().put("filename", file.getName());
                doc.getMetadata().put("processTime", System.currentTimeMillis());
            }
            System.out.println("分片前Document数量: " + docs.size());

            // ==================== 方式一：Spring AI 原生 TokenTextSplitter ====================
            // 特点：简单直接，适合演示“固定大小切块”。
            // 使用时建议把上面的清洗切换为 DocumentClearHandler.clearDocumentsForFlatSplit(docs)
            //List<Document> result = TokenTextSplitterSplit.split(docs);

            // ==================== 方式二：自定义 Overlap 分片 ====================
            // 特点：可以控制 chunk overlap，适合讲解“上下文重叠”。
            // 使用时同样建议使用 DocumentClearHandler.clearDocumentsForFlatSplit(docs)
            //OverlapParagraphTextSplit split = new OverlapParagraphTextSplit(300, 80);
            //List<Document> result = split.apply(docs);

            // ==================== 方式三：Spring AI Alibaba 递归分片 ====================
            // 特点：优先尊重段落、换行、句号等自然边界，是更贴近真实 RAG 的通用方案。
            // 这里使用的是“实战版配置”，和文档里为了展示效果而写的 100 字符示例不同：
            // - chunkSize 调大，避免技术文档被切得过碎
            // - 不再按空格继续拆，避免 Java / JVM / API 这类术语单独成块
            // - 过滤空 chunk，并保留 parent_document_id / chunk_index / total_chunks 元数据
            List<Document> result = SpringAiAlibabaRecursiveTextSplit.split(docs);
            System.out.println("分片后Document数量: " + result.size());
            return result;
        } catch (Exception e) {
            log.error("处理文档失败: {}", file.getName(), e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }
}
