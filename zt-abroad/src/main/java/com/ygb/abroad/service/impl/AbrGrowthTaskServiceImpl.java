package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.mapper.AbrGrowthTaskMapper;
import com.ygb.abroad.service.IAbrGrowthTaskService;

/**
 * 编排任务队列 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrGrowthTaskServiceImpl implements IAbrGrowthTaskService
{
    @Autowired
    private AbrGrowthTaskMapper taskMapper;

    /** 手动重试后的有效期（小时） */
    @Value("${abroad.growth.retry-ttl-hours:24}")
    private int retryTtlHours;

    @Override
    public List<AbrGrowthTask> selectAbrGrowthTaskList(AbrGrowthTask task)
    {
        return taskMapper.selectAbrGrowthTaskList(task);
    }

    @Override
    public AbrGrowthTask selectAbrGrowthTaskById(Long taskId)
    {
        return taskMapper.selectAbrGrowthTaskById(taskId);
    }

    @Override
    public int retryAbrGrowthTasks(Long[] taskIds, String updateBy)
    {
        return taskMapper.resetAbrGrowthTasks(taskIds, retryTtlHours, updateBy);
    }

    @Override
    public int cancelAbrGrowthTasks(Long[] taskIds, String updateBy)
    {
        return taskMapper.cancelAbrGrowthTasks(taskIds, updateBy);
    }
}
