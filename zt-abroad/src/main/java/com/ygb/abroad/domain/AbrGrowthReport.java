package com.ygb.abroad.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 增长周报（M9-04） abr_growth_report
 *
 * @author ygb
 */
public class AbrGrowthReport implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 周报ID */
    private Long reportId;

    /** 所报告的周（周一） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date weekStart;

    /** 数据 JSON */
    private String data;

    /** 摘要与建议 JSON */
    private String narrative;

    /** AI 调用状态 */
    private String aiStatus;

    /** AI 调用日志 */
    private Long aiCallId;

    /** 创建者 */
    private String createBy;

    /** 生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 重新生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getReportId()
    {
        return reportId;
    }

    public void setReportId(Long reportId)
    {
        this.reportId = reportId;
    }

    public Date getWeekStart()
    {
        return weekStart;
    }

    public void setWeekStart(Date weekStart)
    {
        this.weekStart = weekStart;
    }

    public String getData()
    {
        return data;
    }

    public void setData(String data)
    {
        this.data = data;
    }

    public String getNarrative()
    {
        return narrative;
    }

    public void setNarrative(String narrative)
    {
        this.narrative = narrative;
    }

    public String getAiStatus()
    {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus)
    {
        this.aiStatus = aiStatus;
    }

    public Long getAiCallId()
    {
        return aiCallId;
    }

    public void setAiCallId(Long aiCallId)
    {
        this.aiCallId = aiCallId;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("reportId", getReportId())
            .append("weekStart", getWeekStart())
            .append("data", getData())
            .append("narrative", getNarrative())
            .append("aiStatus", getAiStatus())
            .append("aiCallId", getAiCallId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
