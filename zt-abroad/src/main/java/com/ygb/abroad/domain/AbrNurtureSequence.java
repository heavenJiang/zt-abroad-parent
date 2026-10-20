package com.ygb.abroad.domain;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 培育序列 abr_nurture_sequence
 *
 * @author ygb
 */
public class AbrNurtureSequence extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 序列ID */
    private Long sequenceId;

    /** 序列编码 */
    private String sequenceCode;

    /** 名称 */
    private String sequenceName;

    /** 入组事件 */
    private String enrollEvent;

    /** 无互动多少天退出 */
    private Integer inactiveDays;

    /** 状态 */
    private String status;

    /** 步骤 */
    private List<AbrNurtureStep> steps;

    /** 进行中的入组数 */
    private Integer activeCount;

    /** 待确认的入组数 */
    private Integer waitingCount;

    public Long getSequenceId()
    {
        return sequenceId;
    }

    public void setSequenceId(Long sequenceId)
    {
        this.sequenceId = sequenceId;
    }

    public String getSequenceCode()
    {
        return sequenceCode;
    }

    public void setSequenceCode(String sequenceCode)
    {
        this.sequenceCode = sequenceCode;
    }

    public String getSequenceName()
    {
        return sequenceName;
    }

    public void setSequenceName(String sequenceName)
    {
        this.sequenceName = sequenceName;
    }

    public String getEnrollEvent()
    {
        return enrollEvent;
    }

    public void setEnrollEvent(String enrollEvent)
    {
        this.enrollEvent = enrollEvent;
    }

    public Integer getInactiveDays()
    {
        return inactiveDays;
    }

    public void setInactiveDays(Integer inactiveDays)
    {
        this.inactiveDays = inactiveDays;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public List<AbrNurtureStep> getSteps()
    {
        return steps;
    }

    public void setSteps(List<AbrNurtureStep> steps)
    {
        this.steps = steps;
    }

    public Integer getActiveCount()
    {
        return activeCount;
    }

    public void setActiveCount(Integer activeCount)
    {
        this.activeCount = activeCount;
    }

    public Integer getWaitingCount()
    {
        return waitingCount;
    }

    public void setWaitingCount(Integer waitingCount)
    {
        this.waitingCount = waitingCount;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("sequenceId", getSequenceId())
            .append("sequenceCode", getSequenceCode())
            .append("sequenceName", getSequenceName())
            .append("enrollEvent", getEnrollEvent())
            .append("inactiveDays", getInactiveDays())
            .append("status", getStatus())
            .append("steps", getSteps())
            .append("activeCount", getActiveCount())
            .append("waitingCount", getWaitingCount())
            .toString();
    }
}
