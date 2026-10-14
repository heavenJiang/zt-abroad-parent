package com.ygb.abroad.domain;

import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.BaseEntity;

/**
 * AI 调用日志表 abr_ai_call_log
 * 
 * @author ygb
 */
public class AbrAiCallLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 调用ID */
    @Excel(name = "调用ID")
    private Long callId;

    /** Agent 编码 */
    @Excel(name = "Agent 编码")
    private String agentCode;

    /** 提示词版本 */
    @Excel(name = "提示词版本")
    private String promptVersion;

    /** 渲染后提示词的 SHA-256 */
    private String promptHash;

    /** 最终使用的模型 */
    @Excel(name = "模型")
    private String modelUsed;

    /** 是否切换到备用模型（Y是 N否） */
    @Excel(name = "切换备用", readConverterExp = "Y=是,N=否")
    private String fallbackUsed;

    /** 模型调用次数 */
    private Integer attempts;

    /** 结果状态（字典 abr_ai_call_status） */
    @Excel(name = "状态", dictType = "abr_ai_call_status")
    private String status;

    /** 错误、降级或过滤原因 */
    private String errorMsg;

    /** 输入摘要（去标识化） */
    private String inputSummary;

    /** 模型输出（去标识化） */
    private String output;

    /** 输入 token */
    @Excel(name = "输入 token")
    private Integer promptTokens;

    /** 输出 token */
    @Excel(name = "输出 token")
    private Integer completionTokens;

    /** 费用（元） */
    @Excel(name = "费用")
    private BigDecimal cost;

    /** 总耗时（毫秒） */
    @Excel(name = "耗时(ms)")
    private Integer latencyMs;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 审核记录ID */
    private Long reviewId;

    public void setCallId(Long callId)
    {
        this.callId = callId;
    }

    public Long getCallId()
    {
        return callId;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setPromptVersion(String promptVersion)
    {
        this.promptVersion = promptVersion;
    }

    public String getPromptVersion()
    {
        return promptVersion;
    }

    public void setPromptHash(String promptHash)
    {
        this.promptHash = promptHash;
    }

    public String getPromptHash()
    {
        return promptHash;
    }

    public void setModelUsed(String modelUsed)
    {
        this.modelUsed = modelUsed;
    }

    public String getModelUsed()
    {
        return modelUsed;
    }

    public void setFallbackUsed(String fallbackUsed)
    {
        this.fallbackUsed = fallbackUsed;
    }

    public String getFallbackUsed()
    {
        return fallbackUsed;
    }

    public void setAttempts(Integer attempts)
    {
        this.attempts = attempts;
    }

    public Integer getAttempts()
    {
        return attempts;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setInputSummary(String inputSummary)
    {
        this.inputSummary = inputSummary;
    }

    public String getInputSummary()
    {
        return inputSummary;
    }

    public void setOutput(String output)
    {
        this.output = output;
    }

    public String getOutput()
    {
        return output;
    }

    public void setPromptTokens(Integer promptTokens)
    {
        this.promptTokens = promptTokens;
    }

    public Integer getPromptTokens()
    {
        return promptTokens;
    }

    public void setCompletionTokens(Integer completionTokens)
    {
        this.completionTokens = completionTokens;
    }

    public Integer getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCost(BigDecimal cost)
    {
        this.cost = cost;
    }

    public BigDecimal getCost()
    {
        return cost;
    }

    public void setLatencyMs(Integer latencyMs)
    {
        this.latencyMs = latencyMs;
    }

    public Integer getLatencyMs()
    {
        return latencyMs;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("callId", getCallId())
            .append("agentCode", getAgentCode())
            .append("promptVersion", getPromptVersion())
            .append("promptHash", getPromptHash())
            .append("modelUsed", getModelUsed())
            .append("fallbackUsed", getFallbackUsed())
            .append("attempts", getAttempts())
            .append("status", getStatus())
            .append("errorMsg", getErrorMsg())
            .append("inputSummary", getInputSummary())
            .append("output", getOutput())
            .append("promptTokens", getPromptTokens())
            .append("completionTokens", getCompletionTokens())
            .append("cost", getCost())
            .append("latencyMs", getLatencyMs())
            .append("bizType", getBizType())
            .append("bizId", getBizId())
            .append("reviewId", getReviewId())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
