package com.ygb.abroad.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * AI 调用结果
 *
 * @author ygb
 */
public class AiResult
{
    /** 状态（success / degraded / filtered / pending_review / error，见 AiConstants） */
    private String status;

    /** 解析后的输出（已还原占位符）；只有 success 时有值（评测模式下待审核内容也返回） */
    private JsonNode output;

    /** 调用日志ID */
    private Long callId;

    /** 审核记录ID（进入审核队列或被抽检时） */
    private Long reviewId;

    /** Agent 编码 */
    private String agentCode;

    /** 审核策略 */
    private String reviewPolicy;

    /** 提示词版本 */
    private String promptVersion;

    /** 最终使用的模型 */
    private String modelUsed;

    /** 是否切换到备用模型 */
    private boolean fallbackUsed;

    /** 模型调用次数 */
    private int attempts;

    /** 输入 token */
    private int promptTokens;

    /** 输出 token */
    private int completionTokens;

    /** 费用（元） */
    private BigDecimal cost = BigDecimal.ZERO;

    /** 总耗时（毫秒） */
    private long latencyMs;

    /** 输出过滤命中项 */
    private List<AiFilterHit> flags = new ArrayList<>();

    /** 说明（失败原因或提示） */
    private String message;

    /**
     * 是否可直接使用输出
     *
     * @return true 成功
     */
    public boolean isSuccess()
    {
        return AiConstants.STATUS_SUCCESS.equals(status);
    }

    /**
     * 输出是否需要使用者确认后才能写入（审核策略 user_confirm）
     *
     * @return true 需要确认
     */
    public boolean isUserConfirmRequired()
    {
        return AiConstants.POLICY_USER_CONFIRM.equals(reviewPolicy);
    }

    /**
     * 输出是否仅内部可见（审核策略 internal_only），不得发给学生、家长
     *
     * @return true 仅内部可见
     */
    public boolean isInternalOnly()
    {
        return AiConstants.POLICY_INTERNAL_ONLY.equals(reviewPolicy);
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public JsonNode getOutput()
    {
        return output;
    }

    public void setOutput(JsonNode output)
    {
        this.output = output;
    }

    public Long getCallId()
    {
        return callId;
    }

    public void setCallId(Long callId)
    {
        this.callId = callId;
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getReviewPolicy()
    {
        return reviewPolicy;
    }

    public void setReviewPolicy(String reviewPolicy)
    {
        this.reviewPolicy = reviewPolicy;
    }

    public String getPromptVersion()
    {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion)
    {
        this.promptVersion = promptVersion;
    }

    public String getModelUsed()
    {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed)
    {
        this.modelUsed = modelUsed;
    }

    public boolean isFallbackUsed()
    {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed)
    {
        this.fallbackUsed = fallbackUsed;
    }

    public int getAttempts()
    {
        return attempts;
    }

    public void setAttempts(int attempts)
    {
        this.attempts = attempts;
    }

    public int getPromptTokens()
    {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens)
    {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens)
    {
        this.completionTokens = completionTokens;
    }

    public BigDecimal getCost()
    {
        return cost;
    }

    public void setCost(BigDecimal cost)
    {
        this.cost = cost;
    }

    public long getLatencyMs()
    {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs)
    {
        this.latencyMs = latencyMs;
    }

    public List<AiFilterHit> getFlags()
    {
        return flags;
    }

    public void setFlags(List<AiFilterHit> flags)
    {
        this.flags = flags;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
