package org.javaup.ai.init;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.model.Disease;
import org.javaup.ai.model.Drug;
import org.javaup.ai.service.MedicalKnowledgeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 初始化器
 * @author: 阿星不是程序员
 **/
@Slf4j
@Component
public class MedicalDataInitializer implements CommandLineRunner {

    private final MedicalKnowledgeService knowledgeService;
    private final JdbcTemplate jdbcTemplate;

    public MedicalDataInitializer(MedicalKnowledgeService knowledgeService, JdbcTemplate jdbcTemplate) {
        this.knowledgeService = knowledgeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("开始初始化医疗知识库示例数据...");
        try {
            // 清空旧数据，避免重复
            jdbcTemplate.execute("TRUNCATE TABLE public.vector_store");
            log.info("已清空旧数据");

            importDiseases();
            importDrugs();
            log.info("医疗知识库示例数据初始化完成");
        } catch (Exception e) {
            log.error("初始化医疗知识库数据失败", e);
        }
    }

    private void importDiseases() {
        Disease flu = new Disease();
        flu.setId("D001");
        flu.setName("流行性感冒");
        flu.setSymptoms("高热、头痛、全身肌肉酸痛、乏力、鼻塞、流涕、咽痛、咳嗽");
        flu.setTreatment("对症治疗为主，发病48小时内可使用奥司他韦等抗病毒药物，注意休息和补充水分");
        flu.setDepartment("内科");
        flu.setCategory("呼吸系统疾病");
        knowledgeService.importDiseaseKnowledge(flu);

        Disease hypertension = new Disease();
        hypertension.setId("D002");
        hypertension.setName("高血压");
        hypertension.setSymptoms("头晕、头痛、耳鸣、心悸、眼花、注意力不集中、记忆力减退、手脚麻木");
        hypertension.setTreatment("长期规律服用降压药（如氨氯地平、缬沙坦等），低盐低脂饮食，适量运动，戒烟限酒，定期监测血压");
        hypertension.setDepartment("内科");
        hypertension.setCategory("心血管疾病");
        knowledgeService.importDiseaseKnowledge(hypertension);

        Disease diabetes = new Disease();
        diabetes.setId("D003");
        diabetes.setName("2型糖尿病");
        diabetes.setSymptoms("多饮、多尿、多食、体重下降、视力模糊、皮肤瘙痒、伤口愈合缓慢");
        diabetes.setTreatment("饮食控制和运动为基础，口服降糖药（如二甲双胍）或注射胰岛素，定期监测血糖和糖化血红蛋白");
        diabetes.setDepartment("内科");
        diabetes.setCategory("内分泌疾病");
        knowledgeService.importDiseaseKnowledge(diabetes);

        Disease gastritis = new Disease();
        gastritis.setId("D004");
        gastritis.setName("慢性胃炎");
        gastritis.setSymptoms("上腹部隐痛、腹胀、嗳气、食欲不振、恶心、反酸");
        gastritis.setTreatment("规律饮食，避免辛辣刺激食物，幽门螺杆菌阳性者需三联或四联疗法根除治疗，可服用奥美拉唑等质子泵抑制剂");
        gastritis.setDepartment("消化内科");
        gastritis.setCategory("消化系统疾病");
        knowledgeService.importDiseaseKnowledge(gastritis);

        Disease lumbar = new Disease();
        lumbar.setId("D005");
        lumbar.setName("腰椎间盘突出症");
        lumbar.setSymptoms("腰痛、下肢放射痛、下肢麻木、行走困难、久坐后加重");
        lumbar.setTreatment("急性期卧床休息，口服非甾体抗炎药缓解疼痛，配合理疗和康复锻炼，严重者需手术治疗");
        lumbar.setDepartment("骨科");
        lumbar.setCategory("骨骼肌肉疾病");
        knowledgeService.importDiseaseKnowledge(lumbar);
    }

    private void importDrugs() {
        Drug ibuprofen = new Drug();
        ibuprofen.setId("M001");
        ibuprofen.setName("布洛芬缓释胶囊");
        ibuprofen.setIndications("用于缓解轻至中度疼痛，如头痛、关节痛、偏头痛、牙痛、肌肉痛、神经痛、痛经，也用于普通感冒或流行性感冒引起的发热");
        ibuprofen.setDosage("口服，成人一次1粒（0.3g），一日2次");
        ibuprofen.setPrecautions("消化性溃疡患者禁用，肝肾功能不全者慎用，不宜与其他非甾体抗炎药同时使用");
        ibuprofen.setCategory("解热镇痛药");
        knowledgeService.importDrugKnowledge(ibuprofen);

        Drug metformin = new Drug();
        metformin.setId("M002");
        metformin.setName("盐酸二甲双胍片");
        metformin.setIndications("用于2型糖尿病，特别是肥胖的2型糖尿病患者，可单独使用或与其他降糖药联合使用");
        metformin.setDosage("口服，起始剂量一次0.5g，一日2-3次，随餐服用，可根据血糖逐渐增加剂量，最大日剂量2g");
        metformin.setPrecautions("肾功能不全（eGFR<30）禁用，做增强CT检查前后48小时需停药，长期使用注意监测维生素B12水平");
        metformin.setCategory("降糖药");
        knowledgeService.importDrugKnowledge(metformin);

        Drug amlodipine = new Drug();
        amlodipine.setId("M003");
        amlodipine.setName("苯磺酸氨氯地平片");
        amlodipine.setIndications("用于高血压和慢性稳定性心绞痛的治疗");
        amlodipine.setDosage("口服，起始剂量一次5mg，一日1次，根据血压调整，最大剂量一日10mg");
        amlodipine.setPrecautions("严重低血压患者禁用，肝功能不全者需减量，可能引起踝部水肿和头晕");
        amlodipine.setCategory("降压药");
        knowledgeService.importDrugKnowledge(amlodipine);

        Drug omeprazole = new Drug();
        omeprazole.setId("M004");
        omeprazole.setName("奥美拉唑肠溶胶囊");
        omeprazole.setIndications("用于胃溃疡、十二指肠溃疡、反流性食管炎、胃泌素瘤，也用于幽门螺杆菌的联合根除治疗");
        omeprazole.setDosage("口服，一次20mg，一日1-2次，晨起空腹服用，疗程通常4-8周");
        omeprazole.setPrecautions("长期使用可能增加骨折风险和低镁血症风险，不建议无明确指征的长期使用，服药期间避免与氯吡格雷合用");
        omeprazole.setCategory("消化系统用药");
        knowledgeService.importDrugKnowledge(omeprazole);
    }
}
