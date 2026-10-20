package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 培育序列入组 abr_nurture_enrollment
 *
 * @author ygb
 */
public class AbrNurtureEnrollment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 入组ID */
    private Long enrollmentId;

    /** 序列ID */
    private Long sequenceId;

    /** 序列编码 */
    private String sequenceCode;

    /** 序列名称 */
    private String sequenceName;

    /** 对象类型 */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 对象称呼（线索） */
    private String targetName;

    /** 下一步的顺序 */
    private Integer nextStepNo;

    /** 下一步名称 */
    private String nextStepName;

    /** 下一步到期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextDueTime;

    /** 状态 */
    private String status;

    /** 退出原因 */
    private String exitReason;

    /** 最近一步的结果 */
    private String lastResult;

    /** 最近一次发出时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSentTime;

    /** 入组时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date enrolledTime;

    public Long getEnrollmentId()
    {
        return enrollmentId;
    }

    public void setEnrollmentId(Long enrollmentId)
    {
        this.enrollmentId = enrollmentId;
    }

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

    public String getTargetName()
    {
        return targetName;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public Integer getNextStepNo()
    {
        return nextStepNo;
    }

    public void setNextStepNo(Integer nextStepNo)
    {
        this.nextStepNo = nextStepNo;
    }

    public String getNextStepName()
    {
        return nextStepName;
    }

    public void setNextStepName(String nextStepName)
    {
        this.nextStepName = nextStepName;
    }

    public Date getNextDueTime()
    {
        return nextDueTime;
    }

    public void setNextDueTime(Date nextDueTime)
    {
        this.nextDueTime = nextDueTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getExitReason()
    {
        return exitReason;
    }

    public void setExitReason(String exitReason)
    {
        this.exitReason = exitReason;
    }

    public String getLastResult()
    {
        return lastResult;
    }

    public void setLastResult(String lastResult)
    {
        this.lastResult = lastResult;
    }

    public Date getLastSentTime()
    {
        return lastSentTime;
    }

    public void setLastSentTime(Date lastSentTime)
    {
        this.lastSentTime = lastSentTime;
    }

    public Date getEnrolledTime()
    {
        return enrolledTime;
    }

    public void setEnrolledTime(Date enrolledTime)
    {
        this.enrolledTime = enrolledTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("enrollmentId", getEnrollmentId())
            .append("sequenceId", getSequenceId())
            .append("sequenceCode", getSequenceCode())
            .append("sequenceName", getSequenceName())
            .append("targetType", getTargetType())
            .append("targetId", getTargetId())
            .append("targetName", getTargetName())
            .append("nextStepNo", getNextStepNo())
            .append("nextStepName", getNextStepName())
            .append("nextDueTime", getNextDueTime())
            .append("status", getStatus())
            .append("exitReason", getExitReason())
            .append("lastResult", getLastResult())
            .append("lastSentTime", getLastSentTime())
            .append("enrolledTime", getEnrolledTime())
            .toString();
    }
}
