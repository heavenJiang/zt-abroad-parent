package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 公开项目页生成记录表 abr_site_build
 * 
 * @author ygb
 */
public class AbrSiteBuild extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 触发方式：定时检查 */
    public static final String TRIGGER_SCHEDULE = "schedule";

    /** 触发方式：手动 */
    public static final String TRIGGER_MANUAL = "manual";

    /** 状态：生成中 */
    public static final String STATUS_RUNNING = "running";

    /** 状态：成功 */
    public static final String STATUS_SUCCESS = "success";

    /** 状态：失败 */
    public static final String STATUS_FAILED = "failed";

    /** 记录ID */
    private Long buildId;

    /** 触发方式（schedule 定时检查 / manual 手动） */
    private String triggerType;

    /** 院校库与日期的指纹 */
    private String fingerprint;

    /** 状态（running / success / failed） */
    private String status;

    /** 页面总数（HTML，不含 404） */
    private Integer pageTotal;

    /** 重写的文件数 */
    private Integer writtenCount;

    /** 删除的文件数 */
    private Integer removedCount;

    /** 提交收录的地址数 */
    private Integer submittedCount;

    /** 变化的页面（JSON） */
    private String changes;

    /** 收录提交结果（JSON） */
    private String submitResult;

    /** 失败原因 */
    private String errorMsg;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    public void setBuildId(Long buildId)
    {
        this.buildId = buildId;
    }

    public Long getBuildId()
    {
        return buildId;
    }

    public void setTriggerType(String triggerType)
    {
        this.triggerType = triggerType;
    }

    public String getTriggerType()
    {
        return triggerType;
    }

    public void setFingerprint(String fingerprint)
    {
        this.fingerprint = fingerprint;
    }

    public String getFingerprint()
    {
        return fingerprint;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setPageTotal(Integer pageTotal)
    {
        this.pageTotal = pageTotal;
    }

    public Integer getPageTotal()
    {
        return pageTotal;
    }

    public void setWrittenCount(Integer writtenCount)
    {
        this.writtenCount = writtenCount;
    }

    public Integer getWrittenCount()
    {
        return writtenCount;
    }

    public void setRemovedCount(Integer removedCount)
    {
        this.removedCount = removedCount;
    }

    public Integer getRemovedCount()
    {
        return removedCount;
    }

    public void setSubmittedCount(Integer submittedCount)
    {
        this.submittedCount = submittedCount;
    }

    public Integer getSubmittedCount()
    {
        return submittedCount;
    }

    public void setChanges(String changes)
    {
        this.changes = changes;
    }

    public String getChanges()
    {
        return changes;
    }

    public void setSubmitResult(String submitResult)
    {
        this.submitResult = submitResult;
    }

    public String getSubmitResult()
    {
        return submitResult;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("buildId", getBuildId())
            .append("triggerType", getTriggerType())
            .append("fingerprint", getFingerprint())
            .append("status", getStatus())
            .append("pageTotal", getPageTotal())
            .append("writtenCount", getWrittenCount())
            .append("removedCount", getRemovedCount())
            .append("submittedCount", getSubmittedCount())
            .append("changes", getChanges())
            .append("submitResult", getSubmitResult())
            .append("errorMsg", getErrorMsg())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
