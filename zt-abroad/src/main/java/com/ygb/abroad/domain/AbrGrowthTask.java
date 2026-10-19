package com.ygb.abroad.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 编排任务队列表 abr_growth_task（Agent 运行与对外触达）
 * 
 * @author ygb
 */
public class AbrGrowthTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：待执行 */
    public static final String STATUS_PENDING = "pending";

    /** 状态：执行中 */
    public static final String STATUS_RUNNING = "running";

    /** 状态：已完成 */
    public static final String STATUS_DONE = "done";

    /** 状态：已跳过 */
    public static final String STATUS_SKIPPED = "skipped";

    /** 状态：失败 */
    public static final String STATUS_FAILED = "failed";

    /** 状态：已过期 */
    public static final String STATUS_EXPIRED = "expired";

    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "cancelled";

    /** 对象类型：访客 */
    public static final String TARGET_VISITOR = "visitor";

    /** 对象类型：学生账号 */
    public static final String TARGET_APP_USER = "app_user";

    /** 任务ID */
    private Long taskId;

    /** 来源触发规则ID */
    private Long triggerId;

    /** 来源触发规则名称（查询带出） */
    private String triggerName;

    /** 执行的 Agent */
    private String agentCode;

    /** 动作 */
    private String action;

    /** 来源事件ID */
    private Long sourceEventId;

    /** 生成时生效的剧本编码（JSON 数组） */
    private String playbookCodes;

    /** 对象类型 */
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 触达渠道 */
    private String channel;

    /** 去重键 */
    private String dedupKey;

    /** 任务内容（JSON 对象） */
    private String payload;

    /** 状态 */
    private String status;

    /** 计划执行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date scheduledTime;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    /** 已执行次数 */
    private Integer attempts;

    /** 最多执行次数 */
    private Integer maxAttempts;

    /** 执行认领令牌 */
    private String claimToken;

    /** 最近一次开始执行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedTime;

    /** 执行结果（JSON） */
    private String result;

    /** 失败、跳过或过期原因 */
    private String errorMsg;

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

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

    public void setSourceEventId(Long sourceEventId)
    {
        this.sourceEventId = sourceEventId;
    }

    public Long getSourceEventId()
    {
        return sourceEventId;
    }

    public void setPlaybookCodes(String playbookCodes)
    {
        this.playbookCodes = playbookCodes;
    }

    public String getPlaybookCodes()
    {
        return playbookCodes;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setDedupKey(String dedupKey)
    {
        this.dedupKey = dedupKey;
    }

    public String getDedupKey()
    {
        return dedupKey;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public String getPayload()
    {
        return payload;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setScheduledTime(Date scheduledTime)
    {
        this.scheduledTime = scheduledTime;
    }

    public Date getScheduledTime()
    {
        return scheduledTime;
    }

    public void setExpireTime(Date expireTime)
    {
        this.expireTime = expireTime;
    }

    public Date getExpireTime()
    {
        return expireTime;
    }

    public void setAttempts(Integer attempts)
    {
        this.attempts = attempts;
    }

    public Integer getAttempts()
    {
        return attempts;
    }

    public void setMaxAttempts(Integer maxAttempts)
    {
        this.maxAttempts = maxAttempts;
    }

    public Integer getMaxAttempts()
    {
        return maxAttempts;
    }

    public void setClaimToken(String claimToken)
    {
        this.claimToken = claimToken;
    }

    public String getClaimToken()
    {
        return claimToken;
    }

    public void setStartedTime(Date startedTime)
    {
        this.startedTime = startedTime;
    }

    public Date getStartedTime()
    {
        return startedTime;
    }

    public void setFinishedTime(Date finishedTime)
    {
        this.finishedTime = finishedTime;
    }

    public Date getFinishedTime()
    {
        return finishedTime;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getResult()
    {
        return result;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("taskId", getTaskId())
            .append("triggerId", getTriggerId())
            .append("triggerName", getTriggerName())
            .append("agentCode", getAgentCode())
            .append("action", getAction())
            .append("sourceEventId", getSourceEventId())
            .append("playbookCodes", getPlaybookCodes())
            .append("targetType", getTargetType())
            .append("targetId", getTargetId())
            .append("channel", getChannel())
            .append("dedupKey", getDedupKey())
            .append("payload", getPayload())
            .append("status", getStatus())
            .append("scheduledTime", getScheduledTime())
            .append("expireTime", getExpireTime())
            .append("attempts", getAttempts())
            .append("maxAttempts", getMaxAttempts())
            .append("claimToken", getClaimToken())
            .append("startedTime", getStartedTime())
            .append("finishedTime", getFinishedTime())
            .append("result", getResult())
            .append("errorMsg", getErrorMsg())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
