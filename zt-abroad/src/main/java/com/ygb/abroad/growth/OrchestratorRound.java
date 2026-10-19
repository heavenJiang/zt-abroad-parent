package com.ygb.abroad.growth;

/**
 * 编排器一轮的执行统计
 *
 * @param playbookSwitched 本轮是否写入了剧本切换事件
 * @param nodeEvents 本轮写入的日历节点事件数
 * @param eventsRead 本轮读取的事件数
 * @param tasksCreated 本轮生成的任务数
 * @param tasksRun 本轮执行的任务数
 * @param tasksExpired 本轮过期的任务数
 * @param breakerTrips 本轮触发的熔断数
 * @author ygb
 */
public record OrchestratorRound(boolean playbookSwitched, int nodeEvents, int eventsRead, int tasksCreated, int tasksRun, int tasksExpired,
        int breakerTrips)
{
    /**
     * 本轮是否有任何变化
     *
     * @return 是否有变化
     */
    public boolean busy()
    {
        return playbookSwitched || nodeEvents > 0 || eventsRead > 0 || tasksCreated > 0 || tasksRun > 0 || tasksExpired > 0 || breakerTrips > 0;
    }
}
