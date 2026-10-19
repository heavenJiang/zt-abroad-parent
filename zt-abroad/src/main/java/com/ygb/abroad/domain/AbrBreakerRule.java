package com.ygb.abroad.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 熔断规则表 abr_breaker_rule（指标在时间窗内达到阈值时自动暂停）
 * 
 * @author ygb
 */
public class AbrBreakerRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 范围：逐个 Agent */
    public static final String SCOPE_AGENT = "agent";

    /** 范围：逐个渠道账号（只支持计数类指标） */
    public static final String SCOPE_ACCOUNT = "account";

    /** 范围：全局 */
    public static final String SCOPE_GLOBAL = "global";

    /** 规则ID */
    private Long ruleId;

    /** 规则编码 */
    @NotBlank(message = "规则编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "规则编码只能用小写字母、数字和下划线，以字母开头，2–64 位")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称最多 100 个字符")
    private String ruleName;

    /** 指标 */
    @NotBlank(message = "指标不能为空")
    private String metric;

    /** 范围（agent / global） */
    @NotBlank(message = "范围不能为空")
    private String scope;

    /** 只统计该 Agent */
    private String agentCode;

    /** 统计时间窗（小时） */
    @NotNull(message = "时间窗不能为空")
    @Min(value = 1, message = "时间窗为 1–720 小时")
    @Max(value = 720, message = "时间窗为 1–720 小时")
    private Integer windowHours;

    /** 阈值 */
    @NotNull(message = "阈值不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "阈值须大于 0")
    private BigDecimal threshold;

    /** 最少样本数 */
    @Min(value = 0, message = "最少样本数不能为负数")
    private Integer minSample;

    /** 状态（0正常 1停用） */
    private String status;

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

    public void setRuleName(String ruleName)
    {
        this.ruleName = ruleName;
    }

    public String getRuleName()
    {
        return ruleName;
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

    public void setWindowHours(Integer windowHours)
    {
        this.windowHours = windowHours;
    }

    public Integer getWindowHours()
    {
        return windowHours;
    }

    public void setThreshold(BigDecimal threshold)
    {
        this.threshold = threshold;
    }

    public BigDecimal getThreshold()
    {
        return threshold;
    }

    public void setMinSample(Integer minSample)
    {
        this.minSample = minSample;
    }

    public Integer getMinSample()
    {
        return minSample;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("ruleId", getRuleId())
            .append("ruleCode", getRuleCode())
            .append("ruleName", getRuleName())
            .append("metric", getMetric())
            .append("scope", getScope())
            .append("agentCode", getAgentCode())
            .append("windowHours", getWindowHours())
            .append("threshold", getThreshold())
            .append("minSample", getMinSample())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
