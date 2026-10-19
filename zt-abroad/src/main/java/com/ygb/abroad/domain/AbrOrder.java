package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 订单 abr_order（1a 人工确认收款）
 *
 * @author ygb
 */
public class AbrOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "pending";

    public static final String STATUS_PAID = "paid";

    public static final String STATUS_CANCELLED = "cancelled";

    public static final String STATUS_REFUNDED = "refunded";

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 线索ID */
    private Long leadId;

    /** 线索称呼（查询带出） */
    private String leadName;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 状态 */
    private String status;

    /** 收款方式 */
    private String payMethod;

    /** 收款说明 */
    private String payNote;

    /** 确认收款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date paidTime;

    /** 确认收款人 */
    private String confirmedBy;

    /** 取消或退款原因 */
    private String cancelReason;

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

    public String getProductCode()
    {
        return productCode;
    }

    public void setProductCode(String productCode)
    {
        this.productCode = productCode;
    }

    public String getProductName()
    {
        return productName;
    }

    public void setProductName(String productName)
    {
        this.productName = productName;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPayMethod()
    {
        return payMethod;
    }

    public void setPayMethod(String payMethod)
    {
        this.payMethod = payMethod;
    }

    public String getPayNote()
    {
        return payNote;
    }

    public void setPayNote(String payNote)
    {
        this.payNote = payNote;
    }

    public Date getPaidTime()
    {
        return paidTime;
    }

    public void setPaidTime(Date paidTime)
    {
        this.paidTime = paidTime;
    }

    public String getConfirmedBy()
    {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy)
    {
        this.confirmedBy = confirmedBy;
    }

    public String getCancelReason()
    {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason)
    {
        this.cancelReason = cancelReason;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("orderId", getOrderId())
            .append("orderNo", getOrderNo())
            .append("leadId", getLeadId())
            .append("leadName", getLeadName())
            .append("productCode", getProductCode())
            .append("productName", getProductName())
            .append("amount", getAmount())
            .append("currency", getCurrency())
            .append("status", getStatus())
            .append("payMethod", getPayMethod())
            .append("payNote", getPayNote())
            .append("paidTime", getPaidTime())
            .append("confirmedBy", getConfirmedBy())
            .append("cancelReason", getCancelReason())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
