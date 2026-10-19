package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 获客 Agent 运行配置表 abr_growth_agent（执行模式、抽检、每天 AI 预算、暂停）
 * 
 * @author ygb
 */
public class AbrGrowthAgent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 执行模式：全自动 */
    public static final String MODE_AUTO = "auto";

    /** 执行模式：全自动 + 抽检 */
    public static final String MODE_AUTO_SAMPLE = "auto_sample";

    /** 执行模式：人工确认后发出 */
    public static final String MODE_CONFIRM = "confirm";

    /** 执行模式：停用 */
    public static final String MODE_OFF = "off";

    /** ID */
    private Long agentId;

    /** Agent 编码 */
    private String agentCode;

    /** Agent 名称 */
    private String agentName;

    /** 执行模式 */
    @NotBlank(message = "执行模式不能为空")
    private String execMode;

    /** 试运行达标后切换到的执行模式 */
    private String targetMode;

    /** 抽检比例 0–1 */
    @DecimalMin(value = "0", message = "抽检比例为 0–1")
    @DecimalMax(value = "1", message = "抽检比例为 0–1")
    private BigDecimal sampleRate;

    /** 每天 AI 费用上限（元，为空不限） */
    @DecimalMin(value = "0", message = "预算不能为负数")
    private BigDecimal dailyCostBudget;

    /** 每天 AI token 上限（为空不限） */
    @Min(value = 0, message = "预算不能为负数")
    private Long dailyTokenBudget;

    /** 暂停（Y / N） */
    private String paused;

    /** 暂停原因 */
    private String pauseReason;

    /** 暂停人 */
    private String pausedBy;

    /** 暂停时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pausedTime;

    /** 最近一次恢复时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date resumeTime;

    /** 今天已用 AI 费用（查询带出） */
    private BigDecimal todayCost;

    /** 今天已用 AI token（查询带出） */
    private Long todayTokens;

    /** 当前不能运行的原因（查询带出；能运行为空） */
    private String blockReason;

    public void setAgentId(Long agentId)
    {
        this.agentId = agentId;
    }

    public Long getAgentId()
    {
        return agentId;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAgentName(String agentName)
    {
        this.agentName = agentName;
    }

    public String getAgentName()
    {
        return agentName;
    }

    public void setExecMode(String execMode)
    {
        this.execMode = execMode;
    }

    public String getExecMode()
    {
        return execMode;
    }

    public void setTargetMode(String targetMode)
    {
        this.targetMode = targetMode;
    }

    public String getTargetMode()
    {
        return targetMode;
    }

    public void setSampleRate(BigDecimal sampleRate)
    {
        this.sampleRate = sampleRate;
    }

    public BigDecimal getSampleRate()
    {
        return sampleRate;
    }

    public void setDailyCostBudget(BigDecimal dailyCostBudget)
    {
        this.dailyCostBudget = dailyCostBudget;
    }

    public BigDecimal getDailyCostBudget()
    {
        return dailyCostBudget;
    }

    public void setDailyTokenBudget(Long dailyTokenBudget)
    {
        this.dailyTokenBudget = dailyTokenBudget;
    }

    public Long getDailyTokenBudget()
    {
        return dailyTokenBudget;
    }

    public void setPaused(String paused)
    {
        this.paused = paused;
    }

    public String getPaused()
    {
        return paused;
    }

    public void setPauseReason(String pauseReason)
    {
        this.pauseReason = pauseReason;
    }

    public String getPauseReason()
    {
        return pauseReason;
    }

    public void setPausedBy(String pausedBy)
    {
        this.pausedBy = pausedBy;
    }

    public String getPausedBy()
    {
        return pausedBy;
    }

    public void setPausedTime(Date pausedTime)
    {
        this.pausedTime = pausedTime;
    }

    public Date getPausedTime()
    {
        return pausedTime;
    }

    public void setResumeTime(Date resumeTime)
    {
        this.resumeTime = resumeTime;
    }

    public Date getResumeTime()
    {
        return resumeTime;
    }

    public void setTodayCost(BigDecimal todayCost)
    {
        this.todayCost = todayCost;
    }

    public BigDecimal getTodayCost()
    {
        return todayCost;
    }

    public void setTodayTokens(Long todayTokens)
    {
        this.todayTokens = todayTokens;
    }

    public Long getTodayTokens()
    {
        return todayTokens;
    }

    public void setBlockReason(String blockReason)
    {
        this.blockReason = blockReason;
    }

    public String getBlockReason()
    {
        return blockReason;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("agentId", getAgentId())
            .append("agentCode", getAgentCode())
            .append("agentName", getAgentName())
            .append("execMode", getExecMode())
            .append("targetMode", getTargetMode())
            .append("sampleRate", getSampleRate())
            .append("dailyCostBudget", getDailyCostBudget())
            .append("dailyTokenBudget", getDailyTokenBudget())
            .append("paused", getPaused())
            .append("pauseReason", getPauseReason())
            .append("pausedBy", getPausedBy())
            .append("pausedTime", getPausedTime())
            .append("resumeTime", getResumeTime())
            .append("todayCost", getTodayCost())
            .append("todayTokens", getTodayTokens())
            .append("blockReason", getBlockReason())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
