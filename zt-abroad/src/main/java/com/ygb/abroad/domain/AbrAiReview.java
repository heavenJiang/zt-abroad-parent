package com.ygb.abroad.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * AI 审核队列表 abr_ai_review
 * 
 * @author ygb
 */
public class AbrAiReview extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 审核ID */
    private Long reviewId;

    /** 调用ID */
    private Long callId;

    /** Agent 编码 */
    private String agentCode;

    /** 提示词版本 */
    private String promptVersion;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** AI 原始输出（JSON） */
    private String content;

    /** 审核人修改后的内容（JSON） */
    private String editedContent;

    /** 审核状态（字典 abr_ai_review_status） */
    private String status;

    /** 输出过滤命中项（JSON 数组） */
    private String flags;

    /** 审核人 */
    private String reviewer;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    /** 驳回或修改原因 */
    private String reason;

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setCallId(Long callId)
    {
        this.callId = callId;
    }

    public Long getCallId()
    {
        return callId;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setPromptVersion(String promptVersion)
    {
        this.promptVersion = promptVersion;
    }

    public String getPromptVersion()
    {
        return promptVersion;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContent()
    {
        return content;
    }

    public void setEditedContent(String editedContent)
    {
        this.editedContent = editedContent;
    }

    public String getEditedContent()
    {
        return editedContent;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setFlags(String flags)
    {
        this.flags = flags;
    }

    public String getFlags()
    {
        return flags;
    }

    public void setReviewer(String reviewer)
    {
        this.reviewer = reviewer;
    }

    public String getReviewer()
    {
        return reviewer;
    }

    public void setReviewTime(Date reviewTime)
    {
        this.reviewTime = reviewTime;
    }

    public Date getReviewTime()
    {
        return reviewTime;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getReason()
    {
        return reason;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("reviewId", getReviewId())
            .append("callId", getCallId())
            .append("agentCode", getAgentCode())
            .append("promptVersion", getPromptVersion())
            .append("bizType", getBizType())
            .append("bizId", getBizId())
            .append("content", getContent())
            .append("editedContent", getEditedContent())
            .append("status", getStatus())
            .append("flags", getFlags())
            .append("reviewer", getReviewer())
            .append("reviewTime", getReviewTime())
            .append("reason", getReason())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
