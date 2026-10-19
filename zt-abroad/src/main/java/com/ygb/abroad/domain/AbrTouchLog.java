package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 触达记录表 abr_touch_log（每条对外消息一行；频控按此计数）
 * 
 * @author ygb
 */
public class AbrTouchLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类别：营销 */
    public static final String CATEGORY_MARKETING = "marketing";

    /** 类别：服务提醒 */
    public static final String CATEGORY_SERVICE = "service";

    /** 状态：已发出 */
    public static final String STATUS_SENT = "sent";

    /** 状态：发送失败 */
    public static final String STATUS_FAILED = "failed";

    /** 状态：被拦下 */
    public static final String STATUS_BLOCKED = "blocked";

    /** 触达ID */
    private Long touchId;

    /** 编排任务ID */
    private Long taskId;

    /** 发出的 Agent */
    private String agentCode;

    /** 对象类型 */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 渠道 */
    private String channel;

    /** 类别（marketing / service） */
    private String category;

    /** 状态（sent / failed / blocked） */
    private String status;

    /** 拦下或失败原因 */
    private String reason;

    /** 内容引用 */
    private String contentRef;

    public void setTouchId(Long touchId)
    {
        this.touchId = touchId;
    }

    public Long getTouchId()
    {
        return touchId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getCategory()
    {
        return category;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getReason()
    {
        return reason;
    }

    public void setContentRef(String contentRef)
    {
        this.contentRef = contentRef;
    }

    public String getContentRef()
    {
        return contentRef;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("touchId", getTouchId())
            .append("taskId", getTaskId())
            .append("agentCode", getAgentCode())
            .append("targetType", getTargetType())
            .append("targetId", getTargetId())
            .append("channel", getChannel())
            .append("category", getCategory())
            .append("status", getStatus())
            .append("reason", getReason())
            .append("contentRef", getContentRef())
            .append("createTime", getCreateTime())
            .toString();
    }
}
