package org.javaup.ai.controller;

import jakarta.validation.Valid;
import org.javaup.ai.model.MilvusChunkRequest;
import org.javaup.ai.model.MilvusCollectionStatusResponse;
import org.javaup.ai.model.MilvusSearchResult;
import org.javaup.ai.service.MilvusAdminService;
import org.javaup.ai.service.MilvusKnowledgeService;
import org.javaup.ai.service.MilvusSearchService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
/**
 * 把文档里的 Milvus 操作流程暴露成一组简单接口，便于调试和演示。
 */
@RestController
@RequestMapping("/milvus")
public class MilvusDemoController {

    private final MilvusAdminService milvusAdminService;
    private final MilvusKnowledgeService milvusKnowledgeService;
    private final MilvusSearchService milvusSearchService;

    public MilvusDemoController(MilvusAdminService milvusAdminService,
                                MilvusKnowledgeService milvusKnowledgeService,
                                MilvusSearchService milvusSearchService) {
        this.milvusAdminService = milvusAdminService;
        this.milvusKnowledgeService = milvusKnowledgeService;
        this.milvusSearchService = milvusSearchService;
    }

    /**
     * 查看当前 Collection、索引和加载状态。
     */
    @GetMapping("/status")
    public MilvusCollectionStatusResponse status() {
        return milvusAdminService.getCollectionStatus();
    }

    /**
     * 只重建 Collection，不导入样例数据。
     */
    @PostMapping("/demo/recreate")
    public Map<String, Object> recreateCollection() {
        milvusAdminService.recreateCollection();
        return simpleResult("Milvus Collection 已重建");
    }

    /**
     * 向现有 Collection 追加一批固定演示数据。
     */
    @PostMapping("/demo/load")
    public Map<String, Object> loadDemoData() {
        int size = milvusKnowledgeService.importDemoDocuments();
        return resultWithCount("演示数据导入完成", size);
    }

    /**
     * 完整模拟“删库重来 -> 建表建索引 -> 导入文档块”的流程。
     */
    @PostMapping("/demo/reset-and-load")
    public Map<String, Object> resetAndLoadDemoData() {
        milvusAdminService.recreateCollection();
        int size = milvusKnowledgeService.importDemoDocuments();
        return resultWithCount("Collection 已重建并导入演示数据", size);
    }

    /**
     * 手动写入一个文档块，便于验证增量导入逻辑。
     */
    @PostMapping("/chunks")
    public Map<String, Object> addChunk(@Valid @RequestBody MilvusChunkRequest request) {
        milvusKnowledgeService.importChunk(request);
        return simpleResult("文档块写入成功");
    }

    /**
     * 按 chunkId 删除单条向量记录。
     */
    @DeleteMapping("/chunks/{chunkId}")
    public Map<String, Object> deleteChunk(@PathVariable String chunkId) {
        milvusKnowledgeService.deleteByChunkId(chunkId);
        return simpleResult("文档块删除成功");
    }

    /**
     * 按 docId 删除整篇文档对应的所有 chunk。
     */
    @DeleteMapping("/documents/{docId}")
    public Map<String, Object> deleteByDocId(@PathVariable String docId) {
        milvusKnowledgeService.deleteByDocId(docId);
        return simpleResult("指定 docId 的文档块删除成功");
    }

    /**
     * 支持 query + category/docId 过滤的向量检索。
     * topK 控制返回结果数量，ef 控制 HNSW 检索时的搜索宽度。
     */
    @GetMapping("/search")
    public List<MilvusSearchResult> search(@RequestParam("query") String query,
                                           @RequestParam(name = "topK", required = false) Integer topK,
                                           @RequestParam(name = "category", required = false) String category,
                                           @RequestParam(name = "docId", required = false) String docId,
                                           @RequestParam(name = "threshold", required = false) Double threshold,
                                           @RequestParam(name = "ef", required = false) Integer ef) {
        return milvusSearchService.search(query, topK, category, docId, threshold, ef);
    }

    private Map<String, Object> simpleResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        return result;
    }

    private Map<String, Object> resultWithCount(String message, int count) {
        Map<String, Object> result = simpleResult(message);
        result.put("count", count);
        return result;
    }
}
