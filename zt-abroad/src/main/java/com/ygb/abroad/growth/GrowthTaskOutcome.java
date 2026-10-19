package com.ygb.abroad.growth;

import java.util.Map;

/**
 * 编排任务的执行结果
 *
 * @param kind 结果类型
 * @param message 说明（跳过、重试的原因）
 * @param result 结果内容（写入任务的 result）
 * @author ygb
 */
public record GrowthTaskOutcome(Kind kind, String message, Map<String, Object> result)
{
    /**
     * 结果类型
     */
    public enum Kind
    {
        /** 完成 */
        DONE,
        /** 跳过（不需要执行，如对象已不满足条件） */
        SKIPPED,
        /** 稍后重试（如渠道暂时不可用；达到最多执行次数后为失败） */
        RETRY
    }

    /**
     * 完成
     *
     * @param result 结果内容（可为 null）
     * @return 结果
     */
    public static GrowthTaskOutcome done(Map<String, Object> result)
    {
        return new GrowthTaskOutcome(Kind.DONE, null, result);
    }

    /**
     * 跳过
     *
     * @param reason 原因
     * @return 结果
     */
    public static GrowthTaskOutcome skipped(String reason)
    {
        return new GrowthTaskOutcome(Kind.SKIPPED, reason, null);
    }

    /**
     * 稍后重试
     *
     * @param reason 原因
     * @return 结果
     */
    public static GrowthTaskOutcome retry(String reason)
    {
        return new GrowthTaskOutcome(Kind.RETRY, reason, null);
    }

    /**
     * 第 attempts 次执行失败后，距下一次重试的分钟数：5、10、20、40，之后每 60 分钟
     *
     * @param attempts 已执行次数（≥ 1）
     * @return 分钟数
     */
    public static int retryDelayMinutes(int attempts)
    {
        int n = Math.max(1, attempts);
        return n >= 5 ? 60 : 5 << (n - 1);
    }
}
