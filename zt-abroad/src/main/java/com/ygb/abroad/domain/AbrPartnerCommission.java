package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 合作方成交分成 abr_partner_commission（M9-05）
 *
 * @author ygb
 */
public class AbrPartnerCommission extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分成ID */
    private Long commissionId;

    /** 合作方 */
    private Long referrerId;

    /** 合作方名称 */
    @Excel(name = "合作方")
    private String referrerName;

    /** 合作方推荐码 */
    @Excel(name = "推荐码")
    private String refCode;

    /** 被推荐人记录 */
    private Long referralId;

    /** 线索 */
    private Long leadId;

    /** 学生称呼（导出时只保留首字） */
    @Excel(name = "学生")
    private String leadName;

    /** 订单 */
    private Long orderId;

    /** 订单号 */
    @Excel(name = "订单号")
    private String orderNo;

    /** 产品名称 */
    @Excel(name = "产品")
    private String productName;

    /** 币种 */
    @Excel(name = "币种")
    private String currency;

    /** 订单金额 */
    @Excel(name = "订单金额", cellType = Excel.ColumnType.NUMERIC)
    private BigDecimal orderAmount;

    /** 分成比例（%） */
    @Excel(name = "分成比例（%）", cellType = Excel.ColumnType.NUMERIC)
    private BigDecimal rate;

    /** 分成金额 */
    @Excel(name = "分成金额", cellType = Excel.ColumnType.NUMERIC)
    private BigDecimal commissionAmount;

    /** 订单确认收款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "收款时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date orderPaidTime;

    /** 结算月份 */
    @Excel(name = "结算月份")
    private String settleMonth;

    /** 状态（pending / confirmed / paid / void） */
    @Excel(name = "状态", readConverterExp = "pending=待确认,confirmed=已确认,paid=已发放,void=已作废")
    private String status;

    /** 已发放后订单退款（N 无 / Y 待追回 / D 已处理） */
    @Excel(name = "退款待追回", readConverterExp = "Y=待追回,D=已处理,N=否")
    private String refundFlag;

    /** 作废原因 */
    @Excel(name = "作废原因")
    private String voidReason;

    /** 确认人 */
    private String confirmBy;

    /** 确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date confirmTime;

    /** 发放登记人 */
    private String paidBy;

    /** 发放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发放时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date paidTime;

    /** 发放说明 */
    @Excel(name = "发放说明")
    private String paidNote;

    public Long getCommissionId()
    {
        return commissionId;
    }

    public void setCommissionId(Long commissionId)
    {
        this.commissionId = commissionId;
    }

    public Long getReferrerId()
    {
        return referrerId;
    }

    public void setReferrerId(Long referrerId)
    {
        this.referrerId = referrerId;
    }

    public String getReferrerName()
    {
        return referrerName;
    }

    public void setReferrerName(String referrerName)
    {
        this.referrerName = referrerName;
    }

    public String getRefCode()
    {
        return refCode;
    }

    public void setRefCode(String refCode)
    {
        this.refCode = refCode;
    }

    public Long getReferralId()
    {
        return referralId;
    }

    public void setReferralId(Long referralId)
    {
        this.referralId = referralId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getLeadName()
    {
        return leadName;
    }

    public void setLeadName(String leadName)
    {
        this.leadName = leadName;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getProductName()
    {
        return productName;
    }

    public void setProductName(String productName)
    {
        this.productName = productName;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    public BigDecimal getOrderAmount()
    {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount)
    {
        this.orderAmount = orderAmount;
    }

    public BigDecimal getRate()
    {
        return rate;
    }

    public void setRate(BigDecimal rate)
    {
        this.rate = rate;
    }

    public BigDecimal getCommissionAmount()
    {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount)
    {
        this.commissionAmount = commissionAmount;
    }

    public Date getOrderPaidTime()
    {
        return orderPaidTime;
    }

    public void setOrderPaidTime(Date orderPaidTime)
    {
        this.orderPaidTime = orderPaidTime;
    }

    public String getSettleMonth()
    {
        return settleMonth;
    }

    public void setSettleMonth(String settleMonth)
    {
        this.settleMonth = settleMonth;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRefundFlag()
    {
        return refundFlag;
    }

    public void setRefundFlag(String refundFlag)
    {
        this.refundFlag = refundFlag;
    }

    public String getVoidReason()
    {
        return voidReason;
    }

    public void setVoidReason(String voidReason)
    {
        this.voidReason = voidReason;
    }

    public String getConfirmBy()
    {
        return confirmBy;
    }

    public void setConfirmBy(String confirmBy)
    {
        this.confirmBy = confirmBy;
    }

    public Date getConfirmTime()
    {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime)
    {
        this.confirmTime = confirmTime;
    }

    public String getPaidBy()
    {
        return paidBy;
    }

    public void setPaidBy(String paidBy)
    {
        this.paidBy = paidBy;
    }

    public Date getPaidTime()
    {
        return paidTime;
    }

    public void setPaidTime(Date paidTime)
    {
        this.paidTime = paidTime;
    }

    public String getPaidNote()
    {
        return paidNote;
    }

    public void setPaidNote(String paidNote)
    {
        this.paidNote = paidNote;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("commissionId", getCommissionId())
            .append("referrerId", getReferrerId())
            .append("referrerName", getReferrerName())
            .append("refCode", getRefCode())
            .append("referralId", getReferralId())
            .append("leadId", getLeadId())
            .append("leadName", getLeadName())
            .append("orderId", getOrderId())
            .append("orderNo", getOrderNo())
            .append("productName", getProductName())
            .append("currency", getCurrency())
            .append("orderAmount", getOrderAmount())
            .append("rate", getRate())
            .append("commissionAmount", getCommissionAmount())
            .append("orderPaidTime", getOrderPaidTime())
            .append("settleMonth", getSettleMonth())
            .append("status", getStatus())
            .append("refundFlag", getRefundFlag())
            .append("voidReason", getVoidReason())
            .append("confirmBy", getConfirmBy())
            .append("confirmTime", getConfirmTime())
            .append("paidBy", getPaidBy())
            .append("paidTime", getPaidTime())
            .append("paidNote", getPaidNote())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
