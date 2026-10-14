package com.ygb.abroad.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 调用上下文
 *
 * @author ygb
 */
public class AiContext
{
    /** 业务类型（如 assessment、lead） */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 操作人（写入调用日志 create_by） */
    private String operator;

    /** 需要去标识化的姓名（学生、家长等），替换为 [姓名N] */
    private List<String> knownNames = new ArrayList<>();

    /** 已检索到的事实（院校库记录等，附来源），可选 */
    private List<Map<String, Object>> facts;

    /** 试用：允许调用已停用的 Agent（管理端试用、单元测试） */
    private boolean trial;

    /** 评测：不写审核队列，待审核的内容也随结果返回，便于断言 */
    private boolean evaluation;

    /** 指定模型（provider:model），覆盖 Agent 的主模型且不切备用；评测命令用 */
    private String modelOverride;

    /** 整次调用的截止时刻（System.nanoTime() 刻度），0 表示不限；超过后不再重试、不再切备用模型 */
    private long deadlineNanos;

    /**
     * 创建上下文
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param operator 操作人
     * @return 上下文
     */
    public static AiContext of(String bizType, String bizId, String operator)
    {
        AiContext context = new AiContext();
        context.setBizType(bizType);
        context.setBizId(bizId);
        context.setOperator(operator);
        return context;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public List<String> getKnownNames()
    {
        return knownNames;
    }

    public void setKnownNames(List<String> knownNames)
    {
        this.knownNames = knownNames == null ? new ArrayList<>() : knownNames;
    }

    public List<Map<String, Object>> getFacts()
    {
        return facts;
    }

    public void setFacts(List<Map<String, Object>> facts)
    {
        this.facts = facts;
    }

    public boolean isTrial()
    {
        return trial;
    }

    public void setTrial(boolean trial)
    {
        this.trial = trial;
    }

    public boolean isEvaluation()
    {
        return evaluation;
    }

    public void setEvaluation(boolean evaluation)
    {
        this.evaluation = evaluation;
    }

    public String getModelOverride()
    {
        return modelOverride;
    }

    public void setModelOverride(String modelOverride)
    {
        this.modelOverride = modelOverride;
    }

    /**
     * 设置整次调用的时间预算（从现在算起）：单次模型超时取 Agent 配置与剩余预算的较小值，
     * 预算用完后不再做格式重试或切备用模型，避免调用方已超时放弃而后台仍在消耗 token
     *
     * @param budgetMs 预算（毫秒），不大于 0 表示不限
     * @return 本上下文
     */
    public AiContext withBudget(long budgetMs)
    {
        this.deadlineNanos = budgetMs <= 0 ? 0L : System.nanoTime() + budgetMs * 1_000_000L;
        return this;
    }

    /**
     * 剩余预算
     *
     * @return 剩余毫秒；未设置预算返回 Long.MAX_VALUE
     */
    public long remainingMs()
    {
        if (deadlineNanos == 0L)
        {
            return Long.MAX_VALUE;
        }
        return (deadlineNanos - System.nanoTime()) / 1_000_000L;
    }
}
