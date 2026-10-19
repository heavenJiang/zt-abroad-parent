package com.ygb.abroad.domain.vo;

/**
 * C 端：预约、改约、取消的请求体
 *
 * @author ygb
 */
public class AppBookingBody
{
    /** 产品编码 */
    private String productCode;

    /** 时段ID */
    private Long slotId;

    /** 留言（想咨询的问题；不写手机号） */
    private String note;

    /** 取消原因 */
    private String reason;

    public String getProductCode()
    {
        return productCode;
    }

    public void setProductCode(String productCode)
    {
        this.productCode = productCode;
    }

    public Long getSlotId()
    {
        return slotId;
    }

    public void setSlotId(Long slotId)
    {
        this.slotId = slotId;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
