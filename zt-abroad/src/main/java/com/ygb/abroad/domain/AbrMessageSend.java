package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 消息发送记录 abr_message_send
 *
 * @author ygb
 */
public class AbrMessageSend extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 发送ID */
    private Long sendId;

    /** 业务编码 */
    private String templateCode;

    /** 渠道 */
    private String channel;

    /** 类别 */
    private String category;

    /** 对象类型 */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 接收方（脱敏） */
    private String recipient;

    /** 发出的 Agent */
    private String agentCode;

    /** 编排任务ID */
    private Long taskId;

    /** 结果 */
    private String status;

    /** 原因或平台错误 */
    private String reason;

    /** 平台消息ID */
    private String platformMsgId;

    /** 渲染后的文字 */
    private String rendered;

    public Long getSendId()
    {
        return sendId;
    }

    public void setSendId(Long sendId)
    {
        this.sendId = sendId;
    }

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getRecipient()
    {
        return recipient;
    }

    public void setRecipient(String recipient)
    {
        this.recipient = recipient;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getPlatformMsgId()
    {
        return platformMsgId;
    }

    public void setPlatformMsgId(String platformMsgId)
    {
        this.platformMsgId = platformMsgId;
    }

    public String getRendered()
    {
        return rendered;
    }

    public void setRendered(String rendered)
    {
        this.rendered = rendered;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("sendId", getSendId())
            .append("templateCode", getTemplateCode())
            .append("channel", getChannel())
            .append("category", getCategory())
            .append("targetType", getTargetType())
            .append("targetId", getTargetId())
            .append("recipient", getRecipient())
            .append("agentCode", getAgentCode())
            .append("taskId", getTaskId())
            .append("status", getStatus())
            .append("reason", getReason())
            .append("platformMsgId", getPlatformMsgId())
            .append("rendered", getRendered())
            .toString();
    }
}
