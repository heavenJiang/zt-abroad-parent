package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrOrder;

/**
 * 订单 数据层
 *
 * @author ygb
 */
public interface AbrOrderMapper
{
    /**
     * 查询订单（线索、状态、产品精确；订单号模糊；params.beginTime / endTime 下单日期区间）
     *
     * @param order 查询条件
     * @return 订单集合
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
     * 新增订单
     *
     * @param order 订单
     * @return 结果
     */
    public int insertAbrOrder(AbrOrder order);

    /**
     * 确认收款（只从待确认收款改）
     *
     * @param orderId 订单ID
     * @param payMethod 收款方式
     * @param payNote 收款说明
     * @param operator 确认人
     * @return 影响行数
     */
    public int confirmPaid(@Param("orderId") Long orderId, @Param("payMethod") String payMethod, @Param("payNote") String payNote,
            @Param("operator") String operator);

    /**
     * 改状态（取消：待确认收款 → 已取消；退款：已确认收款 → 已退款）
     *
     * @param orderId 订单ID
     * @param from 原状态
     * @param to 新状态
     * @param reason 原因
     * @param operator 操作人
     * @return 影响行数
     */
    public int changeStatus(@Param("orderId") Long orderId, @Param("from") String from, @Param("to") String to, @Param("reason") String reason,
            @Param("operator") String operator);
}
