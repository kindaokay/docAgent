package org.javaup.ai.model;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 模型对象
 * @author: 阿星不是程序员
 **/
public record OrderInfo(
    String orderId,
    String productName,
    String status,
    String logisticsNo,
    String expectedDelivery,
    boolean canRefund
) {
}
