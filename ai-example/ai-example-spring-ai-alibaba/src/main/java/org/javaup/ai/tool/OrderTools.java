package org.javaup.ai.tool;

import java.util.Locale;
import java.util.Map;

import org.javaup.ai.model.OrderInfo;
import org.javaup.ai.model.ProductInfo;
import org.javaup.ai.model.RefundResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 工具类
 * @author: 阿星不是程序员
 **/
@Component
public class OrderTools {

    private static final Map<String, OrderInfo> ORDER_STORE = Map.of(
        "ord-1001", new OrderInfo("ORD-1001", "蓝牙耳机 Pro", "已发货", "SF123456789", "2026-03-14 18:00 前送达", true),
        "ord-1002", new OrderInfo("ORD-1002", "机械键盘 V87", "已签收", "JD99887766", "2026-03-11 12:00 已签收", false)
    );

    private static final Map<String, ProductInfo> PRODUCT_STORE = Map.of(
        "p-1001", new ProductInfo("P-1001", "蓝牙耳机 Pro", "299.00", 28, "主动降噪、40 小时续航、支持双设备切换"),
        "蓝牙耳机 pro", new ProductInfo("P-1001", "蓝牙耳机 Pro", "299.00", 28, "主动降噪、40 小时续航、支持双设备切换"),
        "p-1002", new ProductInfo("P-1002", "机械键盘 V87", "499.00", 12, "Gasket 结构、热插拔、三模连接"),
        "机械键盘 v87", new ProductInfo("P-1002", "机械键盘 V87", "499.00", 12, "Gasket 结构、热插拔、三模连接")
    );

    @Tool(name = "query_order_status", description = "根据订单号查询订单状态、物流单号、预计送达时间和是否支持退款")
    public OrderInfo queryOrderStatus(
        @ToolParam(description = "订单号，例如 ORD-1001") String orderId) {
        return ORDER_STORE.getOrDefault(normalize(orderId),
            new OrderInfo(orderId, "未知商品", "未查到订单", "N/A", "请先确认订单号是否正确", false));
    }

    @Tool(name = "query_product_detail", description = "根据商品 ID 或商品名称查询价格、库存和卖点")
    public ProductInfo queryProduct(
        @ToolParam(description = "商品 ID 或商品名称") String productQuery) {
        return PRODUCT_STORE.getOrDefault(normalize(productQuery),
            new ProductInfo("UNKNOWN", productQuery, "0.00", 0, "暂未找到商品信息，请转人工客服核实"));
    }

    @Tool(name = "apply_refund", description = "为指定订单提交退款申请，需要提供订单号和退款原因")
    public RefundResult applyRefund(
        @ToolParam(description = "订单号，例如 ORD-1001") String orderId,
        @ToolParam(description = "退款原因，例如商品破损、重复下单") String reason) {
        OrderInfo orderInfo = queryOrderStatus(orderId);
        if (!orderInfo.canRefund()) {
            return new RefundResult(orderInfo.orderId(), false, "N/A",
                "当前订单状态为“" + orderInfo.status() + "”，示例系统不支持直接退款，请联系人工客服处理。");
        }
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "未说明原因";
        return new RefundResult(orderInfo.orderId(), true, "RF-20260313-1001",
            "退款申请已受理，原因：" + normalizedReason + "，预计 1-3 个工作日原路退回。");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

}
