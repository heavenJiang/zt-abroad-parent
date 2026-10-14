package com.ygb.abroad.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AI 用量统计行（按天 + Agent 汇总）
 * 
 * @author ygb
 */
public class AbrAiUsageStat implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 日期（北京时间 yyyy-MM-dd） */
    private String day;

    /** Agent 编码 */
    private String agentCode;

    /** 调用次数 */
    private Long calls;

    /** 成功次数 */
    private Long successCalls;

    /** 错误次数（error） */
    private Long errorCalls;

    /** 输入 token */
    private Long promptTokens;

    /** 输出 token */
    private Long completionTokens;

    /** 费用（元） */
    private BigDecimal cost;

    public void setDay(String day)
    {
        this.day = day;
    }

    public String getDay()
    {
        return day;
    }

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setCalls(Long calls)
    {
        this.calls = calls;
    }

    public Long getCalls()
    {
        return calls;
    }

    public void setSuccessCalls(Long successCalls)
    {
        this.successCalls = successCalls;
    }

    public Long getSuccessCalls()
    {
        return successCalls;
    }

    public void setErrorCalls(Long errorCalls)
    {
        this.errorCalls = errorCalls;
    }

    public Long getErrorCalls()
    {
        return errorCalls;
    }

    public void setPromptTokens(Long promptTokens)
    {
        this.promptTokens = promptTokens;
    }

    public Long getPromptTokens()
    {
        return promptTokens;
    }

    public void setCompletionTokens(Long completionTokens)
    {
        this.completionTokens = completionTokens;
    }

    public Long getCompletionTokens()
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("day", getDay())
            .append("agentCode", getAgentCode())
            .append("calls", getCalls())
            .append("successCalls", getSuccessCalls())
            .append("errorCalls", getErrorCalls())
            .append("promptTokens", getPromptTokens())
            .append("completionTokens", getCompletionTokens())
            .append("cost", getCost())
            .toString();
    }
}
