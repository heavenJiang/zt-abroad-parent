package com.ygb.abroad.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.core.domain.BaseEntity;

/**
 * 熔断信号表 abr_growth_signal（投诉、平台违规提示、差评回复、合规拦截 / 通过、退订）
 * 
 * @author ygb
 */
public class AbrGrowthSignal extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类型：投诉 */
    public static final String TYPE_COMPLAINT = "complaint";

    /** 类型：平台违规提示 */
    public static final String TYPE_PLATFORM_VIOLATION = "platform_violation";

    /** 类型：差评回复 */
    public static final String TYPE_NEGATIVE_REPLY = "negative_reply";

    /** 类型：合规拦截 */
    public static final String TYPE_COMPLIANCE_BLOCK = "compliance_block";

    /** 类型：合规通过 */
    public static final String TYPE_COMPLIANCE_PASS = "compliance_pass";

    /** 类型：退订 */
    public static final String TYPE_UNSUBSCRIBE = "unsubscribe";

    /** 信号ID */
    private Long signalId;

    /** 类型 */
    @NotBlank(message = "信号类型不能为空")
    private String signalType;

    /** 相关 Agent */
    private String agentCode;

    /** 相关渠道账号 */
    @Size(max = 64, message = "账号编码最多 64 个字符")
    private String accountCode;

    /** 相关对象类型 */
    private String targetType;

    /** 相关对象ID */
    private Long targetId;

    /** 相关编排任务ID */
    private Long taskId;

    /** 详情（JSON 对象） */
    private String detail;

    /** 来源（system / manual） */
    private String source;

    public void setSignalId(Long signalId)
    {
        this.signalId = signalId;
    }

    public Long getSignalId()
    {
        return signalId;
    }

    public void setSignalType(String signalType)
    {
        this.signalType = signalType;
    }

    public String getSignalType()
    {
        return signalType;
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

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setDetail(String detail)
    {
        this.detail = detail;
    }

    public String getDetail()
    {
        return detail;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSource()
    {
        return source;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("signalId", getSignalId())
            .append("signalType", getSignalType())
            .append("agentCode", getAgentCode())
            .append("accountCode", getAccountCode())
            .append("targetType", getTargetType())
            .append("targetId", getTargetId())
            .append("taskId", getTaskId())
            .append("detail", getDetail())
            .append("source", getSource())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
