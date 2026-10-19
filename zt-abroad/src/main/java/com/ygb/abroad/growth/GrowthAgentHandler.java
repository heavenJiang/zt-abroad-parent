package com.ygb.abroad.growth;

import com.ygb.abroad.domain.AbrGrowthTask;

/**
 * 获客 Agent 的任务处理器：编排器把到期的任务交给 agentCode 相同的处理器执行
 *
 * 每个获客 Agent 在自己的任务卡里实现一个 Spring Bean；没有处理器的 Agent，任务保持待执行直到过期。
 * 处理器内的大模型调用只经 AI 网关，对外发送只经渠道适配器；同一任务可能因超时或重试被再次执行，处理须幂等。
 *
 * @author ygb
 */
public interface GrowthAgentHandler
{
    /**
     * 处理的 Agent 编码（字典 abr_growth_agent）
     *
     * @return Agent 编码
     */
    public String agentCode();

    /**
     * 执行一条任务（任务内容见 AbrGrowthTask.payload：事件内容、规则参数；playbookCodes 为生成时生效的剧本）
     *
     * @param task 任务
     * @return 结果：完成、跳过或稍后重试；抛出异常视为本次失败，按次数重试
     */
    public GrowthTaskOutcome handle(AbrGrowthTask task);
}
