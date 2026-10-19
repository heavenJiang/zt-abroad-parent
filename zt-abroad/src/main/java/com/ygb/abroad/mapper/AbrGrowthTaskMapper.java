package com.ygb.abroad.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrGrowthTask;

/**
 * 编排任务队列 数据层
 * 
 * @author ygb
 */
public interface AbrGrowthTaskMapper
{
    /**
     * 查询任务列表（带出触发规则名称）
     * 
     * @param task 查询条件（Agent、动作、状态、触发规则、对象、来源事件，创建时间区间 params.beginTime / endTime）
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
     * 各状态的任务数
     * 
     * @return status、count
     */
    public List<Map<String, Object>> countAbrGrowthTasksByStatus();

    /**
     * 新增任务；去重键已存在时不写入
     * 
     * @param task 任务
     * @return 写入行数（已存在为 0）
     */
    public int insertAbrGrowthTask(AbrGrowthTask task);

    /**
     * 待执行且已过期的任务标为已过期
     * 
     * @return 影响行数
     */
    public int expireAbrGrowthTasks();

    /**
     * 执行超时（进程中断等）的任务：未达最多次数的恢复为待执行，否则标为失败
     * 
     * @param staleMinutes 超时分钟数
     * @return 影响行数
     */
    public int recoverStaleAbrGrowthTasks(int staleMinutes);

    /**
     * 认领一批到期的待执行任务（标为执行中、次数加一）
     * 
     * @param agentCodes 有处理器的 Agent
     * @param claimToken 认领令牌
     * @param limit 最多条数
     * @return 认领条数
     */
    public int claimAbrGrowthTasks(@Param("agentCodes") List<String> agentCodes, @Param("claimToken") String claimToken, @Param("limit") int limit);

    /**
     * 按认领令牌查询任务
     * 
     * @param claimToken 认领令牌
     * @return 任务（按计划时间）
     */
    public List<AbrGrowthTask> selectAbrGrowthTasksByClaim(String claimToken);

    /**
     * 写入执行结果（只更新本次认领的任务）
     * 
     * @param task 任务（status、result、errorMsg）
     * @return 影响行数
     */
    public int finishAbrGrowthTask(AbrGrowthTask task);

    /**
     * 本次执行失败，未达最多次数时按延迟恢复为待执行
     * 
     * @param taskId 任务ID
     * @param claimToken 认领令牌
     * @param delayMinutes 延迟分钟数
     * @param errorMsg 失败原因
     * @return 影响行数
     */
    public int retryAbrGrowthTask(@Param("taskId") Long taskId, @Param("claimToken") String claimToken, @Param("delayMinutes") int delayMinutes,
            @Param("errorMsg") String errorMsg);

    /**
     * 手动重试：失败、跳过、过期、取消的任务恢复为待执行（次数清零，有效期从现在起算）
     * 
     * @param taskIds 任务ID
     * @param ttlHours 有效期（小时）
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int resetAbrGrowthTasks(@Param("taskIds") Long[] taskIds, @Param("ttlHours") int ttlHours, @Param("updateBy") String updateBy);

    /**
     * 取消待执行的任务
     * 
     * @param taskIds 任务ID
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int cancelAbrGrowthTasks(@Param("taskIds") Long[] taskIds, @Param("updateBy") String updateBy);
}
