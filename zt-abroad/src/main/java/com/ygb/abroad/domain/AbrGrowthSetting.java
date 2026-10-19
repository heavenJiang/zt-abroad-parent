package com.ygb.abroad.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 获客自动化全局设置表 abr_growth_setting（单行：一键暂停、每周触达上限、免打扰、每天 AI 预算）
 * 
 * @author ygb
 */
public class AbrGrowthSetting extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 设置ID（固定为 1） */
    private Integer settingId;

    /** 全局暂停（Y / N） */
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

    /** 每个对象每 7 天最多营销触达次数 */
    @NotNull(message = "每周触达上限不能为空")
    @Min(value = 0, message = "每周触达上限为 0–50")
    @Max(value = 50, message = "每周触达上限为 0–50")
    private Integer weeklyTouchCap;

    /** 免打扰开始（HH:mm） */
    @NotBlank(message = "免打扰开始时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "免打扰时间格式为 HH:mm")
    private String quietStart;

    /** 免打扰结束（HH:mm） */
    @NotBlank(message = "免打扰结束时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "免打扰时间格式为 HH:mm")
    private String quietEnd;

    /** 全部获客 Agent 每天 AI 费用上限（元，为空不限） */
    @DecimalMin(value = "0", message = "预算不能为负数")
    private BigDecimal dailyCostBudget;

    /** 全部获客 Agent 每天 AI token 上限（为空不限） */
    @Min(value = 0, message = "预算不能为负数")
    private Long dailyTokenBudget;

    /** 今天已用 AI 费用（查询带出） */
    private BigDecimal todayCost;

    /** 今天已用 AI token（查询带出） */
    private Long todayTokens;

    public void setSettingId(Integer settingId)
    {
        this.settingId = settingId;
    }

    public Integer getSettingId()
    {
        return settingId;
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

    public void setWeeklyTouchCap(Integer weeklyTouchCap)
    {
        this.weeklyTouchCap = weeklyTouchCap;
    }

    public Integer getWeeklyTouchCap()
    {
        return weeklyTouchCap;
    }

    public void setQuietStart(String quietStart)
    {
        this.quietStart = quietStart;
    }

    public String getQuietStart()
    {
        return quietStart;
    }

    public void setQuietEnd(String quietEnd)
    {
        this.quietEnd = quietEnd;
    }

    public String getQuietEnd()
    {
        return quietEnd;
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("settingId", getSettingId())
            .append("paused", getPaused())
            .append("pauseReason", getPauseReason())
            .append("pausedBy", getPausedBy())
            .append("pausedTime", getPausedTime())
            .append("resumeTime", getResumeTime())
            .append("weeklyTouchCap", getWeeklyTouchCap())
            .append("quietStart", getQuietStart())
            .append("quietEnd", getQuietEnd())
            .append("dailyCostBudget", getDailyCostBudget())
            .append("dailyTokenBudget", getDailyTokenBudget())
            .append("todayCost", getTodayCost())
            .append("todayTokens", getTodayTokens())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
