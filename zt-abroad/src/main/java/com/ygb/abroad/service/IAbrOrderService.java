package com.ygb.abroad.service;

import java.math.BigDecimal;
import java.util.List;
import com.ygb.abroad.domain.AbrOrder;

/**
 * 订单 服务层（1a 不接支付：员工人工确认收款；服务类产品确认收款后线索进入签约）
 *
 * @author ygb
 */
public interface IAbrOrderService
{
    /** 事件：确认收款 */
    public static final String EVENT_PAID = "order_paid";

    /**
     * 查询订单
     *
     * @param order 查询条件
     * @return 订单
     */
    public List<AbrOrder> selectAbrOrderList(AbrOrder order);

    /**
     * 按ID查询订单
     *
     * @param orderId 订单ID
     * @return 订单
     */
    public AbrOrder selectAbrOrderById(Long orderId);

    /**
     * 为线索新建订单（金额由员工填写；产品有价格区间时超出区间会写入备注提示）
     *
     * @param leadId 线索ID
     * @param productCode 产品编码
     * @param amount 金额
     * @param remark 备注
     * @param operator 操作人
     * @return 订单
     */
    public AbrOrder createOrder(Long leadId, String productCode, BigDecimal amount, String remark, String operator);

    /**
     * 确认收款
     *
     * @param orderId 订单ID
     * @param payMethod 收款方式（字典 abr_pay_method）
     * @param payNote 收款说明
     * @param operator 确认人
     * @return 订单
     */
    public AbrOrder confirmPaid(Long orderId, String payMethod, String payNote, String operator);

    /**
     * 取消（待确认收款的）或退款（已确认收款的），须填原因
     *
     * @param orderId 订单ID
     * @param refund 是否退款
     * @param reason 原因
     * @param operator 操作人
     * @return 订单
     */
    public AbrOrder cancelOrRefund(Long orderId, boolean refund, String reason, String operator);
}
