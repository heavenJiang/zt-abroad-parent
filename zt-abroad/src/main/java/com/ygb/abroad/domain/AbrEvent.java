package com.ygb.abroad.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 业务事件表 abr_event
 * 
 * @author ygb
 */
public class AbrEvent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 事件类型：转化时刻 */
    public static final String TYPE_PLAN_MOMENT = "plan_moment";

    /** 事件类型：生效剧本变化（编排器按营销日历写入） */
    public static final String TYPE_PLAYBOOK_SWITCHED = "playbook_switched";

    /** 事件类型：营销日历节点到达（编排器写入） */
    public static final String TYPE_CALENDAR_NODE = "calendar_node";

    /** 推荐状态：待展示 */
    public static final String RECOMMEND_PENDING = "pending";

    /** 推荐状态：已展示 */
    public static final String RECOMMEND_SHOWN = "shown";

    /** 推荐状态：不展示（未成年等） */
    public static final String RECOMMEND_SUPPRESSED = "suppressed";

    /** 推荐状态：无推荐（规则未配置服务） */
    public static final String RECOMMEND_NONE = "none";

    /** 事件ID */
    private Long eventId;

    /** 编排器读取时：写入已满静置时长（读取位置可越过它） */
    private Boolean settled;

    /** 事件类型 */
    private String eventType;

    /** 访客ID */
    private Long visitorId;

    /** 学生账号ID */
    private Long appUserId;

    /** 关联对象类型 */
    private String refType;

    /** 关联对象ID */
    private Long refId;

    /** 去重键（同一访客同类事件只记一次） */
    private String dedupKey;

    /** 事件内容（JSON） */
    private String payload;

    /** 推荐状态（pending / shown / suppressed / none） */
    private String recommendStatus;

    /** 推荐的服务名称 */
    private String recommendLabel;

    /** 推荐展示时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date shownTime;

    /** 订阅方处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date handledTime;

    public Long getEventId()
    {
        return eventId;
    }

    public void setEventId(Long eventId)
    {
        this.eventId = eventId;
    }

    public Boolean getSettled()
    {
        return settled;
    }

    public void setSettled(Boolean settled)
    {
        this.settled = settled;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
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

    public String getDedupKey()
    {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey)
    {
        this.dedupKey = dedupKey;
    }

    public String getPayload()
    {
        return payload;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public String getRecommendStatus()
    {
        return recommendStatus;
    }

    public void setRecommendStatus(String recommendStatus)
    {
        this.recommendStatus = recommendStatus;
    }

    public String getRecommendLabel()
    {
        return recommendLabel;
    }

    public void setRecommendLabel(String recommendLabel)
    {
        this.recommendLabel = recommendLabel;
    }

    public Date getShownTime()
    {
        return shownTime;
    }

    public void setShownTime(Date shownTime)
    {
        this.shownTime = shownTime;
    }

    public Date getHandledTime()
    {
        return handledTime;
    }

    public void setHandledTime(Date handledTime)
    {
        this.handledTime = handledTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("eventId", getEventId())
            .append("eventType", getEventType())
            .append("visitorId", getVisitorId())
            .append("appUserId", getAppUserId())
            .append("refType", getRefType())
            .append("refId", getRefId())
            .append("dedupKey", getDedupKey())
            .append("payload", getPayload())
            .append("recommendStatus", getRecommendStatus())
            .append("recommendLabel", getRecommendLabel())
            .append("shownTime", getShownTime())
            .append("handledTime", getHandledTime())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
