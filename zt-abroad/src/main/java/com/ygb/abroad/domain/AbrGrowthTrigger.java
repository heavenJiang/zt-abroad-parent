package com.ygb.abroad.domain;

import java.util.Date;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 编排触发规则表 abr_growth_trigger（事件订阅或定时 → 交给某个 Agent 的任务）
 * 
 * @author ygb
 */
public class AbrGrowthTrigger extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类型：事件 */
    public static final String TYPE_EVENT = "event";

    /** 类型：定时 */
    public static final String TYPE_SCHEDULE = "schedule";

    /** 触发规则ID */
    private Long triggerId;

    /** 名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称最多 100 个字符")
    private String triggerName;

    /** 类型（event 事件 / schedule 定时） */
    @NotBlank(message = "类型不能为空")
    private String triggerType;

    /** 订阅的事件类型 */
    @Size(max = 64, message = "事件类型最多 64 个字符")
    private String eventType;

    /** 事件过滤（JSON 对象） */
    private String eventFilter;

    /** 定时表达式（秒 分 时 日 月 周，北京时间） */
    @Size(max = 64, message = "定时表达式最多 64 个字符")
    private String cronExpression;

    /** 限定剧本ID */
    private Long playbookId;

    /** 限定剧本编码（查询带出） */
    private String playbookCode;

    /** 执行的 Agent */
    @NotBlank(message = "Agent 不能为空")
    private String agentCode;

    /** 动作 */
    @NotBlank(message = "动作不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$", message = "动作只能用小写字母、数字和下划线，以字母开头，2–64 位")
    private String action;

    /** 动作参数（JSON 对象） */
    private String actionParams;

    /** 触发后延迟执行（分钟） */
    @Min(value = 0, message = "延迟为 0–10080 分钟")
    @Max(value = 10080, message = "延迟为 0–10080 分钟")
    private Integer delayMinutes;

    /** 任务有效期（小时） */
    @Min(value = 1, message = "有效期为 1–720 小时")
    @Max(value = 720, message = "有效期为 1–720 小时")
    private Integer ttlHours;

    /** 最多执行次数 */
    @Min(value = 1, message = "执行次数为 1–10")
    @Max(value = 10, message = "执行次数为 1–10")
    private Integer maxAttempts;

    /** 定时规则最近一次触发的计划时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFireTime;

    /** 状态（0正常 1停用） */
    private String status;

    public void setTriggerId(Long triggerId)
    {
        this.triggerId = triggerId;
    }

    public Long getTriggerId()
    {
        return triggerId;
    }

    public void setTriggerName(String triggerName)
    {
        this.triggerName = triggerName;
    }

    public String getTriggerName()
    {
        return triggerName;
    }

    public void setTriggerType(String triggerType)
    {
        this.triggerType = triggerType;
    }

    public String getTriggerType()
    {
        return triggerType;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setEventFilter(String eventFilter)
    {
        this.eventFilter = eventFilter;
    }

    public String getEventFilter()
    {
        return eventFilter;
    }

    public void setCronExpression(String cronExpression)
    {
        this.cronExpression = cronExpression;
    }

    public String getCronExpression()
    {
        return cronExpression;
    }

    public void setPlaybookId(Long playbookId)
    {
        this.playbookId = playbookId;
    }

    public Long getPlaybookId()
    {
        return playbookId;
    }

    public void setPlaybookCode(String playbookCode)
    {
        this.playbookCode = playbookCode;
    }

    public String getPlaybookCode()
    {
        return playbookCode;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getAction()
    {
        return action;
    }

    public void setActionParams(String actionParams)
    {
        this.actionParams = actionParams;
    }

    public String getActionParams()
    {
        return actionParams;
    }

    public void setDelayMinutes(Integer delayMinutes)
    {
        this.delayMinutes = delayMinutes;
    }

    public Integer getDelayMinutes()
    {
        return delayMinutes;
    }

    public void setTtlHours(Integer ttlHours)
    {
        this.ttlHours = ttlHours;
    }

    public Integer getTtlHours()
    {
        return ttlHours;
    }

    public void setMaxAttempts(Integer maxAttempts)
    {
        this.maxAttempts = maxAttempts;
    }

    public Integer getMaxAttempts()
    {
        return maxAttempts;
    }

    public void setLastFireTime(Date lastFireTime)
    {
        this.lastFireTime = lastFireTime;
    }

    public Date getLastFireTime()
    {
        return lastFireTime;
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
            .append("triggerId", getTriggerId())
            .append("triggerName", getTriggerName())
            .append("triggerType", getTriggerType())
            .append("eventType", getEventType())
            .append("eventFilter", getEventFilter())
            .append("cronExpression", getCronExpression())
            .append("playbookId", getPlaybookId())
            .append("playbookCode", getPlaybookCode())
            .append("agentCode", getAgentCode())
            .append("action", getAction())
            .append("actionParams", getActionParams())
            .append("delayMinutes", getDelayMinutes())
            .append("ttlHours", getTtlHours())
            .append("maxAttempts", getMaxAttempts())
            .append("lastFireTime", getLastFireTime())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
