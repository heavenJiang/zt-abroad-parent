package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrGrowthTask;

/**
 * 编排任务队列 服务层（管理端查看、重试、取消；生成与执行见 IAbrOrchestratorService）
 * 
 * @author ygb
 */
public interface IAbrGrowthTaskService
{
    /**
     * 查询任务列表
     * 
     * @param task 查询条件
     * @return 任务集合
     */
    public List<AbrGrowthTask> selectAbrGrowthTaskList(AbrGrowthTask task);

    /**
     * 按ID查询任务
     * 
     * @param taskId 任务ID
     * @return 任务
     */
    public AbrGrowthTask selectAbrGrowthTaskById(Long taskId);

    /**
     * 手动重试：失败、跳过、过期、取消的任务恢复为待执行（次数清零，有效期从现在起算）
     * 
     * @param taskIds 任务ID
     * @param updateBy 操作人
     * @return 恢复的条数
     */
    public int retryAbrGrowthTasks(Long[] taskIds, String updateBy);

    /**
     * 取消待执行的任务
     * 
     * @param taskIds 任务ID
     * @param updateBy 操作人
     * @return 取消的条数
     */
    public int cancelAbrGrowthTasks(Long[] taskIds, String updateBy);
}
