package com.ygb.abroad.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrPartnerCommission;

/**
 * 合作方分成 数据层（M9-05）
 * 
 * @author ygb
 */
public interface AbrPartnerMapper
{
    /**
     * 为已确认收款、经合作方链接进入的订单补写待确认分成（每个订单一条，已有的不变）
     * 
     * @param defaultRate 合作方没设比例时的默认比例（%）
     * @param defaultWindow 合作方没设时的归因有效天数
     * @param operator 操作人
     * @return 新写入条数
     */
    public int insertCommissions(@Param("defaultRate") BigDecimal defaultRate, @Param("defaultWindow") int defaultWindow,
            @Param("operator") String operator);

    /**
     * 订单已取消或退款：未发放的分成作废
     * 
     * @param operator 操作人
     * @return 作废条数
     */
    public int voidCancelledOrders(@Param("operator") String operator);

    /**
     * 订单已退款：已发放的分成标记待追回
     * 
     * @param operator 操作人
     * @return 标记条数
     */
    public int flagRefundedPaid(@Param("operator") String operator);

    /**
     * 查询分成列表
     * 
     * @param commission 条件（referrerId、status、settleMonth、refundFlag）
     * @return 分成集合
     */
    public List<AbrPartnerCommission> selectCommissionList(AbrPartnerCommission commission);

    /**
     * 待确认 → 已确认
     */
    public int confirmCommissions(@Param("ids") Long[] ids, @Param("operator") String operator);

    /**
     * 已确认 → 已发放
     */
    public int payCommissions(@Param("ids") Long[] ids, @Param("note") String note, @Param("operator") String operator);

    /**
     * 待确认 / 已确认 → 作废
     */
    public int voidCommission(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 已发放的待追回标记处理完毕（线下已追回或抵扣）
     */
    public int clearRefundFlag(@Param("id") Long id, @Param("note") String note, @Param("operator") String operator);

    /**
     * 月度对账单：每个合作方每个币种一行
     * 
     * @param month 结算月份 yyyy-MM
     * @return referrer_id、referrer_name、ref_code、currency、orders、order_amount、commission_amount、各状态金额、refund_flags
     */
    public List<Map<String, Object>> selectStatement(@Param("month") String month);
}
