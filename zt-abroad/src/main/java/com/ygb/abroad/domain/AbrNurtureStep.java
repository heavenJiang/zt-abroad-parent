package com.ygb.abroad.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 培育序列的步骤 abr_nurture_step
 *
 * @author ygb
 */
public class AbrNurtureStep extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 步骤ID */
    private Long stepId;

    /** 序列ID */
    private Long sequenceId;

    /** 顺序 */
    private Integer stepNo;

    /** 名称 */
    private String stepName;

    /** 入组后第几天 */
    private Integer delayDays;

    /** 几点发 */
    private Integer sendHour;

    /** 消息模板业务编码 */
    private String templateCode;

    /** 条件 */
    private String conditionCode;

    /** 限定剧本（JSON 数组文本） */
    private String playbookCodes;

    /** 状态 */
    private String status;

    public Long getStepId()
    {
        return stepId;
    }

    public void setStepId(Long stepId)
    {
        this.stepId = stepId;
    }

    public Long getSequenceId()
    {
        return sequenceId;
    }

    public void setSequenceId(Long sequenceId)
    {
        this.sequenceId = sequenceId;
    }

    public Integer getStepNo()
    {
        return stepNo;
    }

    public void setStepNo(Integer stepNo)
    {
        this.stepNo = stepNo;
    }

    public String getStepName()
    {
        return stepName;
    }

    public void setStepName(String stepName)
    {
        this.stepName = stepName;
    }

    public Integer getDelayDays()
    {
        return delayDays;
    }

    public void setDelayDays(Integer delayDays)
    {
        this.delayDays = delayDays;
    }

    public Integer getSendHour()
    {
        return sendHour;
    }

    public void setSendHour(Integer sendHour)
    {
        this.sendHour = sendHour;
    }

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getConditionCode()
    {
        return conditionCode;
    }

    public void setConditionCode(String conditionCode)
    {
        this.conditionCode = conditionCode;
    }

    public String getPlaybookCodes()
    {
        return playbookCodes;
    }

    public void setPlaybookCodes(String playbookCodes)
    {
        this.playbookCodes = playbookCodes;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("stepId", getStepId())
            .append("sequenceId", getSequenceId())
            .append("stepNo", getStepNo())
            .append("stepName", getStepName())
            .append("delayDays", getDelayDays())
            .append("sendHour", getSendHour())
            .append("templateCode", getTemplateCode())
            .append("conditionCode", getConditionCode())
            .append("playbookCodes", getPlaybookCodes())
            .append("status", getStatus())
            .toString();
    }
}
