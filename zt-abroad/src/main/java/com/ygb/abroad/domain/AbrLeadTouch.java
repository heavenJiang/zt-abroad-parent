package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 线索来源与触达记录 abr_lead_touch
 *
 * @author ygb
 */
public class AbrLeadTouch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long touchId;

    /** 线索ID */
    private Long leadId;

    /** 来源 */
    private String source;

    /** 类型 */
    private String touchType;

    /** 渠道码 */
    private String channelCode;

    /** 内容编号 */
    private String contentCode;

    /** 关联对象类型 */
    private String refType;

    /** 关联对象ID */
    private Long refId;

    /** 来源业务事件 */
    private Long eventId;

    /** 摘要 */
    private String summary;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date occurredTime;

    public Long getTouchId()
    {
        return touchId;
    }

    public void setTouchId(Long touchId)
    {
        this.touchId = touchId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getTouchType()
    {
        return touchType;
    }

    public void setTouchType(String touchType)
    {
        this.touchType = touchType;
    }

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getContentCode()
    {
        return contentCode;
    }

    public void setContentCode(String contentCode)
    {
        this.contentCode = contentCode;
    }

    public String getRefType()
    {
        return refType;
    }

    public void setRefType(String refType)
    {
        this.refType = refType;
    }

    public Long getRefId()
    {
        return refId;
    }

    public void setRefId(Long refId)
    {
        this.refId = refId;
    }

    public Long getEventId()
    {
        return eventId;
    }

    public void setEventId(Long eventId)
    {
        this.eventId = eventId;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public Date getOccurredTime()
    {
        return occurredTime;
    }

    public void setOccurredTime(Date occurredTime)
    {
        this.occurredTime = occurredTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("touchId", getTouchId())
            .append("leadId", getLeadId())
            .append("source", getSource())
            .append("touchType", getTouchType())
            .append("channelCode", getChannelCode())
            .append("contentCode", getContentCode())
            .append("refType", getRefType())
            .append("refId", getRefId())
            .append("eventId", getEventId())
            .append("summary", getSummary())
            .append("occurredTime", getOccurredTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
