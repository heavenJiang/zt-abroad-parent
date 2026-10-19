package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 熔断与预算闸触发记录表 abr_breaker_trip
 * 
 * @author ygb
 */
public class AbrBreakerTrip extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long tripId;

    /** 熔断规则ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 指标 */
    private String metric;

    /** 范围 */
    private String scope;

    /** 暂停的 Agent */
    private String agentCode;

    /** 暂停的渠道账号 */
    private String accountCode;

    /** 触发时的指标值 */
    private BigDecimal metricValue;

    /** 样本数 */
    private Integer sampleSize;

    /** 阈值 */
    private BigDecimal threshold;

    /** 统计起点 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date windowStart;

    /** 动作 */
    private String action;

    /** 去重键 */
    private String dedupKey;

    /** 恢复人 */
    private String resolvedBy;

    /** 恢复时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date resolvedTime;

    public void setTripId(Long tripId)
    {
        this.tripId = tripId;
    }

    public Long getTripId()
    {
        return tripId;
    }

    public void setRuleId(Long ruleId)
    {
        this.ruleId = ruleId;
    }

    public Long getRuleId()
    {
        return ruleId;
    }

    public void setRuleCode(String ruleCode)
    {
        this.ruleCode = ruleCode;
    }

    public String getRuleCode()
    {
        return ruleCode;
    }

    public void setMetric(String metric)
    {
        this.metric = metric;
    }

    public String getMetric()
    {
        return metric;
    }

    public void setScope(String scope)
    {
        this.scope = scope;
    }

    public String getScope()
    {
        return scope;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAccountCode(String accountCode)
    {
        this.accountCode = accountCode;
    }

    public String getAccountCode()
    {
        return accountCode;
    }

    public void setMetricValue(BigDecimal metricValue)
    {
        this.metricValue = metricValue;
    }

    public BigDecimal getMetricValue()
    {
        return metricValue;
    }

    public void setSampleSize(Integer sampleSize)
    {
        this.sampleSize = sampleSize;
    }

    public Integer getSampleSize()
    {
        return sampleSize;
    }

    public void setThreshold(BigDecimal threshold)
    {
        this.threshold = threshold;
    }

    public BigDecimal getThreshold()
    {
        return threshold;
    }

    public void setWindowStart(Date windowStart)
    {
        this.windowStart = windowStart;
    }

    public Date getWindowStart()
    {
        return windowStart;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getAction()
    {
        return action;
    }

    public void setDedupKey(String dedupKey)
    {
        this.dedupKey = dedupKey;
    }

    public String getDedupKey()
    {
        return dedupKey;
    }

    public void setResolvedBy(String resolvedBy)
    {
        this.resolvedBy = resolvedBy;
    }

    public String getResolvedBy()
    {
        return resolvedBy;
    }

    public void setResolvedTime(Date resolvedTime)
    {
        this.resolvedTime = resolvedTime;
    }

    public Date getResolvedTime()
    {
        return resolvedTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("tripId", getTripId())
            .append("ruleId", getRuleId())
            .append("ruleCode", getRuleCode())
            .append("metric", getMetric())
            .append("scope", getScope())
            .append("agentCode", getAgentCode())
            .append("accountCode", getAccountCode())
            .append("metricValue", getMetricValue())
            .append("sampleSize", getSampleSize())
            .append("threshold", getThreshold())
            .append("windowStart", getWindowStart())
            .append("action", getAction())
            .append("dedupKey", getDedupKey())
            .append("resolvedBy", getResolvedBy())
            .append("resolvedTime", getResolvedTime())
            .append("createTime", getCreateTime())
            .toString();
    }
}
