package org.javaup.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.Disease;
import org.javaup.ai.model.Drug;
import org.javaup.ai.service.MedicalKnowledgeService;
import org.javaup.ai.service.MedicalSearchService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
@Slf4j
@RestController
@RequestMapping("/medical")
public class MedicalController {

    private final MedicalKnowledgeService knowledgeService;
    private final MedicalSearchService searchService;

    public MedicalController(MedicalKnowledgeService knowledgeService,
                             MedicalSearchService searchService) {
        this.knowledgeService = knowledgeService;
        this.searchService = searchService;
    }

    /**
     * 导入疾病知识
     */
    @PostMapping("/disease")
    public String importDisease(@RequestBody Disease disease) {
        knowledgeService.importDiseaseKnowledge(disease);
        return "导入成功：" + disease.getName();
    }

    /**
     * 导入药品知识
     */
    @PostMapping("/drug")
    public String importDrug(@RequestBody Drug drug) {
        knowledgeService.importDrugKnowledge(drug);
        return "导入成功：" + drug.getName();
    }

    /**
     * 通用检索
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam("query") String query,
                                            @RequestParam(name = "topK", defaultValue = "5") int topK) {
        List<Document> docs = searchService.search(query, topK);
        return toResultList(docs);
    }

    /**
     * 按科室检索
     */
    @GetMapping("/search/department")
    public List<Map<String, Object>> searchByDepartment(@RequestParam("query") String query,
                                                        @RequestParam("department") String department,
                                                        @RequestParam(name = "topK", defaultValue = "5") int topK) {
        List<Document> docs = searchService.searchByDepartment(query, department, topK);
        return toResultList(docs);
    }

    /**
     * 只搜索药品
     */
    @GetMapping("/search/drug")
    public List<Map<String, Object>> searchDrugs(@RequestParam("query") String query,
                                                 @RequestParam(name = "topK", defaultValue = "5") int topK) {
        List<Document> docs = searchService.searchDrugs(query, topK);
        return toResultList(docs);
    }

    private List<Map<String, Object>> toResultList(List<Document> docs) {
        return docs.stream()
                .map(doc -> Map.<String, Object>of(
                        "content", doc.getText(),
                        "metadata", doc.getMetadata()
                ))
                .collect(Collectors.toList());
    }
}
