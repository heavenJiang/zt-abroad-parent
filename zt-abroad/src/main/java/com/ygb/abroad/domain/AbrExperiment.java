package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * A/B 实验（M9-04） abr_experiment
 *
 * @author ygb
 */
public class AbrExperiment extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 实验ID */
    private Long experimentId;

    /** 名称 */
    private String experimentName;

    /** 类型（hook_copy） */
    private String experimentType;

    /** 平台 */
    private String platform;

    /** 指标（click_rate / tool_rate） */
    private String metric;

    /** 分组 JSON */
    private String variants;

    /** 每组最少样本 */
    private Integer minSample;

    /** 最长运行天数 */
    private Integer maxDays;

    /** 显著性水平 */
    private BigDecimal alpha;

    /** 状态 */
    private String status;

    /** 胜出组 */
    private String winnerCode;

    /** 最近一次评估结果 JSON */
    private String result;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束 / 停止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    /** 最近评估时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date evaluatedTime;

    /** 结论说明 */
    private String conclusionReason;

    /** 已分组的内容数 */
    private Integer unitCount;

    public Long getExperimentId()
    {
        return experimentId;
    }

    public void setExperimentId(Long experimentId)
    {
        this.experimentId = experimentId;
    }

    public String getExperimentName()
    {
        return experimentName;
    }

    public void setExperimentName(String experimentName)
    {
        this.experimentName = experimentName;
    }

    public String getExperimentType()
    {
        return experimentType;
    }

    public void setExperimentType(String experimentType)
    {
        this.experimentType = experimentType;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getMetric()
    {
        return metric;
    }

    public void setMetric(String metric)
    {
        this.metric = metric;
    }

    public String getVariants()
    {
        return variants;
    }

    public void setVariants(String variants)
    {
        this.variants = variants;
    }

    public Integer getMinSample()
    {
        return minSample;
    }

    public void setMinSample(Integer minSample)
    {
        this.minSample = minSample;
    }

    public Integer getMaxDays()
    {
        return maxDays;
    }

    public void setMaxDays(Integer maxDays)
    {
        this.maxDays = maxDays;
    }

    public BigDecimal getAlpha()
    {
        return alpha;
    }

    public void setAlpha(BigDecimal alpha)
    {
        this.alpha = alpha;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getWinnerCode()
    {
        return winnerCode;
    }

    public void setWinnerCode(String winnerCode)
    {
        this.winnerCode = winnerCode;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    public Date getEvaluatedTime()
    {
        return evaluatedTime;
    }

    public void setEvaluatedTime(Date evaluatedTime)
    {
        this.evaluatedTime = evaluatedTime;
    }

    public String getConclusionReason()
    {
        return conclusionReason;
    }

    public void setConclusionReason(String conclusionReason)
    {
        this.conclusionReason = conclusionReason;
    }

    public Integer getUnitCount()
    {
        return unitCount;
    }

    public void setUnitCount(Integer unitCount)
    {
        this.unitCount = unitCount;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("experimentId", getExperimentId())
            .append("experimentName", getExperimentName())
            .append("experimentType", getExperimentType())
            .append("platform", getPlatform())
            .append("metric", getMetric())
            .append("variants", getVariants())
            .append("minSample", getMinSample())
            .append("maxDays", getMaxDays())
            .append("alpha", getAlpha())
            .append("status", getStatus())
            .append("winnerCode", getWinnerCode())
            .append("result", getResult())
            .append("startTime", getStartTime())
            .append("endTime", getEndTime())
            .append("evaluatedTime", getEvaluatedTime())
            .append("conclusionReason", getConclusionReason())
            .append("unitCount", getUnitCount())
            .toString();
    }
}
