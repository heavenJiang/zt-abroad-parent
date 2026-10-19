package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 预约 abr_booking
 *
 * @author ygb
 */
public class AbrBooking extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_BOOKED = "booked";

    public static final String STATUS_CANCELLED = "cancelled";

    public static final String STATUS_DONE = "done";

    public static final String STATUS_NO_SHOW = "no_show";

    /** 预约ID */
    private Long bookingId;

    /** 线索ID */
    private Long leadId;

    /** 主访客ID */
    private Long visitorId;

    /** 学生账号ID */
    private Long appUserId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 顾问时段 */
    private Long slotId;

    /** 顾问 */
    private Long consultantId;

    /** 顾问姓名（查询带出） */
    private String consultantName;

    /** 线索称呼（查询带出） */
    private String leadName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 方式 */
    private String mode;

    /** 状态 */
    private String status;

    /** 来源（app / staff / agent） */
    private String source;

    /** 留言或备注 */
    private String note;

    /** 取消原因 */
    private String cancelReason;

    /** 改约次数 */
    private Integer rescheduleCount;

    public Long getBookingId()
    {
        return bookingId;
    }

    public void setBookingId(Long bookingId)
    {
        this.bookingId = bookingId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public Long getVisitorId()
    {
        return visitorId;
    }

    public void setVisitorId(Long visitorId)
    {
        this.visitorId = visitorId;
    }

    public Long getAppUserId()
    {
        return appUserId;
    }

    public void setAppUserId(Long appUserId)
    {
        this.appUserId = appUserId;
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

    public Long getSlotId()
    {
        return slotId;
    }

    public void setSlotId(Long slotId)
    {
        this.slotId = slotId;
    }

    public Long getConsultantId()
    {
        return consultantId;
    }

    public void setConsultantId(Long consultantId)
    {
        this.consultantId = consultantId;
    }

    public String getConsultantName()
    {
        return consultantName;
    }

    public void setConsultantName(String consultantName)
    {
        this.consultantName = consultantName;
    }

    public String getLeadName()
    {
        return leadName;
    }

    public void setLeadName(String leadName)
    {
        this.leadName = leadName;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getCancelReason()
    {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason)
    {
        this.cancelReason = cancelReason;
    }

    public Integer getRescheduleCount()
    {
        return rescheduleCount;
    }

    public void setRescheduleCount(Integer rescheduleCount)
    {
        this.rescheduleCount = rescheduleCount;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("bookingId", getBookingId())
            .append("leadId", getLeadId())
            .append("visitorId", getVisitorId())
            .append("appUserId", getAppUserId())
            .append("productCode", getProductCode())
            .append("productName", getProductName())
            .append("slotId", getSlotId())
            .append("consultantId", getConsultantId())
            .append("consultantName", getConsultantName())
            .append("leadName", getLeadName())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("mode", getMode())
            .append("status", getStatus())
            .append("source", getSource())
            .append("note", getNote())
            .append("cancelReason", getCancelReason())
            .append("rescheduleCount", getRescheduleCount())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
