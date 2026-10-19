package com.ygb.abroad.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.abroad.growth.OrchestratorRound;

/**
 * 营销编排器 服务层：按营销日历切换剧本、事件与定时触发、执行编排任务
 * 
 * @author ygb
 */
public interface IAbrOrchestratorService
{
    /**
     * 执行一轮（定时任务每分钟调用）：日历 → 事件触发 → 定时触发 → 过期与超时恢复 → 执行到期任务
     * 
     * @return 本轮统计；另一轮正在执行时返回 null
     */
    public OrchestratorRound dispatch();

    /**
     * 某天生效的剧本（Agent 取当前剧本时用）
     * 
     * @param date 日期（北京时间）
     * @return 剧本（按优先级从大到小）
     */
    public List<AbrPlaybook> activePlaybooks(LocalDate date);

    /**
     * 今天的编排概况：生效剧本、今天的节点、有处理器的 Agent、各状态任务数、启用的定时规则下次触发时间
     * 
     * @return 概况
     */
    public Map<String, Object> current();

    /**
     * 预览一段时间内的剧本切换与节点
     * 
     * @param from 开始日期
     * @param days 天数（1–400）
     * @return segments 剧本组合不变的区间、nodes 节点
     */
    public Map<String, Object> preview(LocalDate from, int days);

    /**
     * 手动触发一次规则（不受事件与定时限制，立即生成一条待执行任务；规则停用时也可用于试运行）
     * 
     * @param triggerId 触发规则ID
     * @param operator 操作人
     * @return 生成的任务
     */
    public AbrGrowthTask fireTrigger(Long triggerId, String operator);
}
