package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 顾问可预约时段 abr_consult_slot
 *
 * @author ygb
 */
public class AbrConsultSlot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_OPEN = "open";

    public static final String STATUS_BOOKED = "booked";

    public static final String STATUS_CLOSED = "closed";

    /** 时段ID */
    private Long slotId;

    /** 顾问 */
    private Long userId;

    /** 顾问姓名（查询带出） */
    private String consultantName;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 方式 */
    private String mode;

    /** 状态（open / booked / closed） */
    private String status;

    /** 占用的预约 */
    private Long bookingId;

    public Long getSlotId()
    {
        return slotId;
    }

    public void setSlotId(Long slotId)
    {
        this.slotId = slotId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getConsultantName()
    {
        return consultantName;
    }

    public void setConsultantName(String consultantName)
    {
        this.consultantName = consultantName;
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

    public Long getBookingId()
    {
        return bookingId;
    }

    public void setBookingId(Long bookingId)
    {
        this.bookingId = bookingId;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("slotId", getSlotId())
            .append("userId", getUserId())
            .append("consultantName", getConsultantName())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("mode", getMode())
            .append("status", getStatus())
            .append("bookingId", getBookingId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
