package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 线索卡片 abr_lead_card（仅内部可见）
 *
 * @author ygb
 */
public class AbrLeadCard extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：AI 生成 */
    public static final String STATUS_DONE = "done";

    /** 状态：只有基础信息 */
    public static final String STATUS_FALLBACK = "fallback";

    /** 卡片ID */
    private Long cardId;

    /** 线索ID */
    private Long leadId;

    /** 状态（done / fallback） */
    private String cardStatus;

    /** 基础信息（JSON） */
    private String basics;

    /** AI 内容（JSON） */
    private String aiContent;

    /** 模型输入摘要 */
    @JsonIgnore
    private String inputDigest;

    /** AI 调用ID */
    private Long aiCallId;

    /** AI 调用状态 */
    private String aiStatus;

    /** 失败原因 */
    private String errorMsg;

    /** 触发 */
    private String triggerType;

    /** 生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date generatedTime;

    public Long getCardId()
    {
        return cardId;
    }

    public void setCardId(Long cardId)
    {
        this.cardId = cardId;
    }

    public Long getLeadId()
    {
        return leadId;
    }

    public void setLeadId(Long leadId)
    {
        this.leadId = leadId;
    }

    public String getCardStatus()
    {
        return cardStatus;
    }

    public void setCardStatus(String cardStatus)
    {
        this.cardStatus = cardStatus;
    }

    public String getBasics()
    {
        return basics;
    }

    public void setBasics(String basics)
    {
        this.basics = basics;
    }

    public String getAiContent()
    {
        return aiContent;
    }

    public void setAiContent(String aiContent)
    {
        this.aiContent = aiContent;
    }

    public String getInputDigest()
    {
        return inputDigest;
    }

    public void setInputDigest(String inputDigest)
    {
        this.inputDigest = inputDigest;
    }

    public Long getAiCallId()
    {
        return aiCallId;
    }

    public void setAiCallId(Long aiCallId)
    {
        this.aiCallId = aiCallId;
    }

    public String getAiStatus()
    {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus)
    {
        this.aiStatus = aiStatus;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getTriggerType()
    {
        return triggerType;
    }

    public void setTriggerType(String triggerType)
    {
        this.triggerType = triggerType;
    }

    public Date getGeneratedTime()
    {
        return generatedTime;
    }

    public void setGeneratedTime(Date generatedTime)
    {
        this.generatedTime = generatedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("cardId", getCardId())
            .append("leadId", getLeadId())
            .append("cardStatus", getCardStatus())
            .append("inputDigest", getInputDigest())
            .append("aiCallId", getAiCallId())
            .append("aiStatus", getAiStatus())
            .append("errorMsg", getErrorMsg())
            .append("triggerType", getTriggerType())
            .append("generatedTime", getGeneratedTime())
            .toString();
    }
}
