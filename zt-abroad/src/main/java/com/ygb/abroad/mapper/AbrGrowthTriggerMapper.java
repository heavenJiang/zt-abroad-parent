package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrGrowthTrigger;

/**
 * 编排触发规则 数据层
 * 
 * @author ygb
 */
public interface AbrGrowthTriggerMapper
{
    /**
     * 查询触发规则列表（带出限定剧本编码）
     * 
     * @param trigger 查询条件（名称模糊、类型、事件类型、Agent、状态）
     * @return 触发规则集合
     */
    public List<AbrGrowthTrigger> selectAbrGrowthTriggerList(AbrGrowthTrigger trigger);

    /**
     * 按ID查询触发规则
     * 
     * @param triggerId 触发规则ID
     * @return 触发规则
     */
    public AbrGrowthTrigger selectAbrGrowthTriggerById(Long triggerId);

    /**
     * 新增触发规则
     * 
     * @param trigger 触发规则
     * @return 结果
     */
    public int insertAbrGrowthTrigger(AbrGrowthTrigger trigger);

    /**
     * 修改触发规则
     * 
     * @param trigger 触发规则
     * @return 结果
     */
    public int updateAbrGrowthTrigger(AbrGrowthTrigger trigger);

    /**
     * 批量删除触发规则
     * 
     * @param triggerIds 触发规则ID
     * @return 结果
     */
    public int deleteAbrGrowthTriggerByIds(Long[] triggerIds);

    /**
     * 记录定时规则的触发时刻（只能向后推进，防止重复触发）
     * 
     * @param triggerId 触发规则ID
     * @param fireTime 计划时刻
     * @return 影响行数（已记录更晚的时刻为 0）
     */
    public int updateLastFireTime(@Param("triggerId") Long triggerId, @Param("fireTime") Date fireTime);
}
